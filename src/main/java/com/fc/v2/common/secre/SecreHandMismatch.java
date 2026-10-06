package com.fc.v2.common.secre;

/**
 * 报文与册面对不上的一行：逐条点名，点到原报行次与件码。
 *
 * <p>行上带出的题名与名录代号一律以载体册面为凭，报文里那一串只作对照；
 * 两处对不上，行仍按册面收下，但对不上的这一行要在回执里指名报出。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class SecreHandMismatch {

    /** 原报行次（机关纸面名册上的那一行） */
    private Integer rowNo;

    /** 这一行的载体件码 */
    private String itemCode;

    /** 对不上落在哪一栏（content 题名／bookNo 名录代号） */
    private String field;

    /** 对不上的缘由：报的是什么、册面是什么、以册面为准 */
    private String msg;

    public SecreHandMismatch() {
    }

    public SecreHandMismatch(Integer rowNo, String itemCode, String field, String msg) {
        this.rowNo = rowNo;
        this.itemCode = itemCode;
        this.field = field;
        this.msg = msg;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
