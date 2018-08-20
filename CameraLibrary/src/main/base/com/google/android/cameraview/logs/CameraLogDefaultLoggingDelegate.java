/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.cameraview.logs;

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * LoggingDelegate的默认实现就是println方式输出日志
 *
 * Created by javayhu on 8/19/17.
 */
public class CameraLogDefaultLoggingDelegate implements LoggingDelegate {

    public static final CameraLogDefaultLoggingDelegate sInstance =
            new CameraLogDefaultLoggingDelegate();

    private String mApplicationTag = "camera";
    private int mMinimumLoggingLevel = Log.INFO;

    private CameraLogDefaultLoggingDelegate() {
    }

    public static CameraLogDefaultLoggingDelegate getInstance() {
        return sInstance;
    }

    private static String getMsg(String msg, Throwable tr) {
        return msg + '\n' + getStackTraceString(tr);
    }

    private static String getStackTraceString(Throwable tr) {
        if (tr == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Sets an application tag that is used for checking if a log line is loggable and also
     * to prefix to all log lines.
     *
     * @param tag the tag
     */
    public void setApplicationTag(String tag) {
        mApplicationTag = tag;
    }

    @Override
    public int getMinimumLoggingLevel() {
        return mMinimumLoggingLevel;
    }

    @Override
    public void setMinimumLoggingLevel(int level) {
        mMinimumLoggingLevel = level;
    }

    @Override
    public boolean isLoggable(int level) {
        return mMinimumLoggingLevel <= level;
    }

    @Override
    public void v(String tag, String msg) {
        println(Log.VERBOSE, tag, msg);
    }

    @Override
    public void v(String tag, String msg, Throwable tr) {
        println(Log.VERBOSE, tag, msg, tr);
    }

    @Override
    public void d(String tag, String msg) {
        println(Log.DEBUG, tag, msg);
    }

    @Override
    public void d(String tag, String msg, Throwable tr) {
        println(Log.DEBUG, tag, msg, tr);
    }

    @Override
    public void i(String tag, String msg) {
        println(Log.INFO, tag, msg);
    }

    @Override
    public void i(String tag, String msg, Throwable tr) {
        println(Log.INFO, tag, msg, tr);
    }

    @Override
    public void w(String tag, String msg) {
        println(Log.WARN, tag, msg);
    }

    @Override
    public void w(String tag, String msg, Throwable tr) {
        println(Log.WARN, tag, msg, tr);
    }

    @Override
    public void e(String tag, String msg) {
        println(Log.ERROR, tag, msg);
    }

    @Override
    public void e(String tag, String msg, Throwable tr) {
        println(Log.ERROR, tag, msg, tr);
    }

    /**
     * <p> Note: this gets forwarded to {@code android.util.Log.e} as {@code android.util.Log.wtf}
     * might crash the app.
     */
    @Override
    public void wtf(String tag, String msg) {
        println(Log.ERROR, tag, msg);
    }

    /**
     * <p> Note: this gets forwarded to {@code android.util.Log.e} as {@code android.util.Log.wtf}
     * might crash the app.
     */
    @Override
    public void wtf(String tag, String msg, Throwable tr) {
        println(Log.ERROR, tag, msg, tr);
    }

    @Override
    public void log(int priority, String tag, String msg) {
        println(priority, tag, msg);
    }

    private void println(int priority, String tag, String msg) {
        Log.println(priority, prefixTag(tag), msg);
    }

    private void println(int priority, String tag, String msg, Throwable tr) {
        Log.println(priority, prefixTag(tag), getMsg(msg, tr));
    }

    private String prefixTag(String tag) {
        if (mApplicationTag != null) {
            return mApplicationTag + ":" + tag;
        } else {
            return tag;
        }
    }
}