package com.example.file_management.controller.vo;

import java.util.List;

public class MultiDataVO<T> extends BaseVO {
    private List<T> dataList;

    public MultiDataVO() {
    }

    public MultiDataVO(int code, long time, boolean success, String errorMessage, List<T> dataList) {
        super(code, time, success, errorMessage);
        this.dataList = dataList;
    }

    public List<T> getData() {
        return dataList;
    }

    public void setData(List<T> dataList) {
        this.dataList = dataList;
    }

    public static <T> MultiDataVO<T> buildDataVO(int code, long time, boolean success, String errorMessage, List<T> dataList) {
        return new MultiDataVO<>(code, time, success, errorMessage, dataList);
    }
}
