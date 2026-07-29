package com.example.file_management.controller;

public class BaseVO {
    private int code;
    private long time;
    private boolean success;
    private String errorMessage;

    public BaseVO(int code, long time, boolean success, String errorMessage) {
        this.code = code;
        this.time = time;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public BaseVO() {
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "BaseVO{" +
                "code=" + code +
                ", time=" + time +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
    public static BaseVO buildVO(int code, long time, boolean success, String errorMessage){
        return new BaseVO(code, time, success, errorMessage);
    }
}
