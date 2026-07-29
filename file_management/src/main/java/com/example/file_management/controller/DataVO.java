package com.example.file_management.controller;

//查询类接口的返回体：BaseVO的状态字段 + 数据本体
public class DataVO<T> extends BaseVO {
    private T data;

    public DataVO() {
    }

    public DataVO(int code, long time, boolean success, String errorMessage, T data) {
        super(code, time, success, errorMessage);
        this.data = data;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public static <T> DataVO<T> buildDataVO(int code, long time, boolean success, String errorMessage, T data) {
        return new DataVO<>(code, time, success, errorMessage, data);
    }
}
