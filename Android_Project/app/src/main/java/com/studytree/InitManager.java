package com.studytree;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiskCache;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.cache.memory.impl.LruMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.nostra13.universalimageloader.core.download.BaseImageDownloader;
import com.studytree.bean.DeviceInfoBean;
import com.studytree.bean.PictureBean;
import com.studytree.bean.UserBean;
import com.studytree.commonfile.Constants;
import com.studytree.log.Logger;
import com.studytree.utils.StringUtils;
import com.umeng.commonsdk.UMConfigure;
import com.umeng.message.IUmengRegisterCallback;
import com.umeng.message.PushAgent;
import com.umeng.socialize.PlatformConfig;

import java.io.File;

/**
 * 应用初始化管理
 * Title: InitManager
 * @date 2018/6/13 15:27
 * @author Freedom0013
 */
public class InitManager {
    private static final String TAG = InitManager.class.getSimpleName();
    /** InitManager单例 */
    private static InitManager _instance = null;
    /** Context对象 */
    public Context mContext;
    /** 是否初始化过标识 */
    public boolean inited = false;
    /** SharedPreferences对象 */
    public SharedPreferences mConfigPrefs;
    /** 屏幕宽度 */
    public int mScreenWidth = -1;
    /** 屏幕高度 */
    public int mScreenHeight = -1;
    /** LayoutInflater布局服务对象 */
    public LayoutInflater mInflater;
    /** ImageLoader缓存图片存储目录 */
    public File ImageLoader_Cache_dir = new File(Constants.IMAGE_DIR);
    /** 设备信息DeviceUtils中引用 */
    public DeviceInfoBean devceinfo;
    /** 用户信息Bean */
    private UserBean mUserBean;

    /**
     * 私有构造函数
     */
    private InitManager(){
    }

    /**
     * 获取InitManager单例
     * @return InitManager对象
     */
    public static InitManager getInstance(){
        if (_instance == null) {
            synchronized (InitManager.class) {
                if (_instance == null) {
                    _instance = new InitManager();
                }
            }
        }
        return _instance;
    }

    /**
     * 初始化应用
     */
    public void init(Context context){
        if(inited){
            return;
        }
        inited = true;
        this.mContext = context;
        //初始化异常处理类
        ExceptionHandler.getInstance().init(mContext.getApplicationContext());
        //初始化友盟统计
        UMConfigure.init(mContext,Constants.UMAppKey, Constants.UMChannelID, UMConfigure.DEVICE_TYPE_PHONE ,Constants.UMMessageSecret);
        //设置是否显示友盟log
        UMConfigure.setLogEnabled(Constants.UM_Log_STATES);
        //初始化友盟推送
        PushAgent mPushAgent = PushAgent.getInstance(mContext);
        //注册推送服务，每次调用register方法都会回调该接口
        mPushAgent.register(new IUmengRegisterCallback() {
            @Override
            public void onSuccess(String deviceToken) {
                //注册成功会返回device token
                //手动获取device token，可以调用mPushAgent.getRegistrationId()方法（需在注册成功后调用）
                Logger.d(TAG, "友盟：deviceToken = " + deviceToken);
            }

            @Override
            public void onFailure(String s, String s1) {
                Logger.e(TAG, "友盟Push注册失败！" + s + "|||" + s1);
            }
        });

        //TODO：等待QQ、微信应用AppKey
        PlatformConfig.setWeixin("", "");
        PlatformConfig.setSinaWeibo("616658324", "13b450fe7cd86de1c91021601148cda8","http://sns.whalecloud.com");
        PlatformConfig.setQQZone("", "");

        //ImageLoader初始化设置（ImageLoader建造者设计模式）
        ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(mContext)
            .threadPoolSize(5) // 线程池大小
            .threadPriority(Thread.NORM_PRIORITY - 2) // 设置线程优先级
            .denyCacheImageMultipleSizesInMemory() // 不允许在内存中缓存同一张图片的多个尺寸
            .tasksProcessingOrder(QueueProcessingType.LIFO) // 设置处理队列的类型，包括LIFO， FIFO
            .memoryCache(new LruMemoryCache(3 * 1024 * 1024)) // 内存缓存策略
            .memoryCacheSize(5 * 1024 * 1024)  // 内存缓存大小
            .memoryCacheExtraOptions(480, 800) // 内存缓存中每个图片的最大宽高
            .memoryCacheSizePercentage(50) // 内存缓存占总内存的百分比
            .diskCache(new UnlimitedDiskCache(ImageLoader_Cache_dir)) // 设置磁盘缓存路径
            .diskCacheSize(50 * 1024 * 1024) // 设置磁盘缓存的大小
            .diskCacheFileCount(50) // 磁盘缓存文件数量
            .diskCacheFileNameGenerator(new Md5FileNameGenerator()) // 磁盘缓存时图片名称加密方式
            .imageDownloader(new BaseImageDownloader(mContext,60 * 1000, 60 * 1000)) // 图片下载器(后面数字为超时时间)
            .defaultDisplayImageOptions(DisplayImageOptions.createSimple())
            .writeDebugLogs() // 打印日志
            .build();
        //ImageLoader初始化
        ImageLoader.getInstance().init(config);
    }

    /**
     * 初始化保存的用户信息
     */
    public UserBean getUserInfo() {
        if (mUserBean == null) {
            String userinfoStr = getStringPreference(Constants.PREF_USER_INFO);
            if (!StringUtils.isNullOrEmpty(userinfoStr)) {
                try {
                    Gson gson = new Gson();
                    JsonObject data = new JsonParser().parse(userinfoStr).getAsJsonObject();
                    JsonArray dataArray = data.getAsJsonArray("data");
                    //解析数据
                    JsonElement userinfo = dataArray.get(0);
                    JsonObject userinfos = new JsonParser().parse(userinfo.toString()).getAsJsonObject();
                    JsonElement userdata = userinfos.getAsJsonObject("userdata");
                    mUserBean = gson.fromJson(userdata, UserBean.class);
                    //解析头像
                    //解析数据
                    JsonElement picinfo = dataArray.get(1);
                    JsonObject picinfos = new JsonParser().parse(picinfo.toString()).getAsJsonObject();
                    JsonElement picdata = picinfos.getAsJsonObject("pic");
                    PictureBean picture = gson.fromJson(picdata, PictureBean.class);
                    mUserBean.user_picture_url = "http://" + Constants.HOST + "/" + picture.picture_img;
                } catch (Exception e) {
                    mUserBean = null;
                }
            }
        }
        return mUserBean;
    }

    /**
     * 本地保存用户信息
     * @param user 用户Bean
     * @param userinfo JsonObject
     */
    public void setUserInfo(UserBean user, JsonObject userinfo) {
        Logger.d(TAG, "保存用户信息: " + (userinfo == null ? "null" : userinfo.toString()));
        if (user == null || userinfo == null) {
            saveStringPreference(Constants.PREF_USER_INFO, "");
        } else {
            saveStringPreference(Constants.PREF_USER_INFO, userinfo.toString());
        }
        mUserBean = user;
    }

    /**
     * 保存用户登录名、密码
     * @param phone 登录名
     * @param password 密码
     */
    public void savePhoneAndPasswordToPrefs(String phone, String password) {
        if (StringUtils.isNullOrEmpty(phone) || StringUtils.isNullOrEmpty(password)) {
            saveStringPreference(Constants.PREF_LOGIN_IN_SAVE, "");
        } else {
            saveStringPreference(Constants.PREF_LOGIN_IN_SAVE, phone + "_" + password);
        }
    }

    /**
     * 获取屏幕宽度
     * @return 屏幕宽度（获取失败返回-1）
     */
    public int getScreenWidth() {
        if (mScreenWidth == -1) {
            mScreenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
        }
        return mScreenWidth;
    }

    /**
     * 获取屏幕高度
     * @return 屏幕高度（获取失败返回-1）
     */
    public int getScreenHeight() {
        if (mScreenHeight == -1) {
            mScreenHeight = mContext.getResources().getDisplayMetrics().heightPixels;
        }
        return mScreenHeight;
    }

    /**
     * 获取LayoutInflater对象
     * @return LayoutInflater
     */
    public LayoutInflater getInflater(){
        if(mInflater == null){
            mInflater = LayoutInflater.from(mContext);
        }
        return mInflater;
    }

    /**
     * 获取Context对象
     * @return Context
     */
    public Context getContext(){
        return mContext;
    }

    /**
     * 获取系统SharedPreferences对象
     * @return SharedPreferences对象
     */
    private SharedPreferences getPrefs(){
        if(mConfigPrefs == null){
            mConfigPrefs = mContext.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        }
        return mConfigPrefs;
    }

    /**
     * 获取String配置项
     * @param key 键
     * @return 值
     */
    public String getStringPreference(String key){
        return getPrefs().getString(key, "");
    }

    /**
     * 保存String配置项
     * @param key 键
     * @param value 值
     */
    public void saveStringPreference(String key,String value){
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString(key, value);
        editor.commit();
    }

    /**
     * 获取Boolean配置项
     * @param key 键
     * @return 值（默认false）
     */
    public boolean getBooleanPreference(String key){
        return getPrefs().getBoolean(key,false);
    }

    /**
     * 保存Boolean配置项
     * @param key 键
     * @param value 值
     */
    public void saveBooleanPreference(String key,boolean value){
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putBoolean(key, value);
        editor.commit();
    }

    /**
     * 保存Int配置项
     * @param key 键
     * @param value 值
     */
    public void saveIntPreference(String key,int value){
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(key, value);
        editor.commit();
    }

    /**
     * 获取默认值Int配置项
     * @param key 键
     * @param defaultValue 默认值
     * @return 值
     */
    public int getIntPreference(String key,int defaultValue){
        int index = getPrefs().getInt(key,defaultValue);
        return index;
    }

    /**
     * 获取Int配置项
     * @param key 键
     * @return 值
     */
    public int getIntPreference(String key){
        int index = getPrefs().getInt(key,-1);
        return index;
    }

    /**
     *获取Long配置项
     * @param key 键
     * @return 值
     */
    public long getLongPreference(String key){
        long index = getPrefs().getLong(key,0L);
        return index;
    }

    /**
     * 保存Long配置项
     * @param key 键
     * @param value 值
     */
    public void saveLongPreference(String key,long value){
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putLong(key, value);
        editor.commit();
    }

}
