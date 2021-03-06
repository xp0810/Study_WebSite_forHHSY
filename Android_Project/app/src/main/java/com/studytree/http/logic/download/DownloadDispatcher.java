package com.studytree.http.logic.download;

import android.support.annotation.NonNull;

import com.studytree.http.HttpTransaction;
import com.studytree.log.Logger;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 下载调度类
 * Title: DownloadDispatcher
 * @date 2018/7/5 20:57
 * @author Freedom0013
 */
public class DownloadDispatcher {
    private static final String TAG = DownloadDispatcher.class.getSimpleName();
    /** 下载调度类单例 */
    private static volatile DownloadDispatcher sDownloadDispatcher;
    /** Java虚拟机可用处理器数量 */
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    /** 线程数 */
    private static final int THREAD_SIZE = Math.max(3, Math.min(CPU_COUNT - 1, 5));
    /** 核心线程数 */
    private static final int CORE_POOL_SIZE = THREAD_SIZE;
    /** 线程池 */
    private ExecutorService mExecutorService;
    /** 当前运行下载队列 */
    private final Deque<DownloadTask> runningTasks = new ArrayDeque<>();

    /** 空参构造函数 */
    private DownloadDispatcher() {
    }

    /**
     * 获取单例
     * @return DownloadDispatcher对象
     */
    public static DownloadDispatcher getInstance() {
        if (sDownloadDispatcher == null) {
            synchronized (DownloadDispatcher.class) {
                if (sDownloadDispatcher == null) {
                    sDownloadDispatcher = new DownloadDispatcher();
                }
            }
        }
        return sDownloadDispatcher;
    }

    /**
     * 创建下载线程池
     * @return 线程池
     */
    public synchronized ExecutorService executorService() {
        if (mExecutorService == null) {
            mExecutorService = new ThreadPoolExecutor(CORE_POOL_SIZE, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(), new ThreadFactory() {
                @Override
                public Thread newThread(@NonNull Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setDaemon(false);
                    return thread;
                }
            });
        }
        return mExecutorService;
    }

    /**
     * 开始下载
     * @param name 下载文件名
     * @param url 下载地址
     * @param callBack 下载回调
     */
    public void startDownload(final String name, final String url, final DownloadCallback callBack) {
        Call call = HttpTransaction.getInstance().asyncCall(url);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callBack.onFailure(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                //获取文件的大小
                long contentLength = response.body().contentLength();
                Logger.d(TAG, "文件总大小：" + contentLength);
                if (contentLength <= -1) {
                    return;
                }
                //创建下载处理
                DownloadTask downloadTask = new DownloadTask(name, url, THREAD_SIZE, contentLength, callBack);
                downloadTask.init();
                runningTasks.add(downloadTask);
            }
        });
    }

    /**
     * 停止下载
     * @param url 地址
     */
    public void stopDownLoad(String url) {
        for (DownloadTask runningTask : runningTasks) {
            if (runningTask.getUrl().equals(url)) {
                runningTask.stopDownload();
            }
        }
    }

    /**
     * 回收下载处理类
     * @param downLoadTask 处理类
     */
    public void recyclerTask(DownloadTask downLoadTask) {
        runningTasks.remove(downLoadTask);
    }
}
