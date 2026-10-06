package com.fc.v2.common.secre;

/**
 * 原路退回的一行：原报行次、件码、落在哪一栏、缘故。
 *
 * <p>没落成的那一行仍要把纸面行次随回执带出去，好让机关那本册子
 * 行行对得上；缘故只写在这一行上，不带累别行。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class SecreHandReturned {

    /** 原报行次（机关纸面名册上的那一行） */
    private Integer rowNo;

    /** 载体件码（空着退回时可能为空，行次仍在） */
    private String itemCode;

    /** 毛病落在哪一栏 */
    private String field;

    /** 退回缘故 */
    private String msg;

    public SecreHandReturned() {
    }

    public SecreHandReturned(Integer rowNo, String itemCode, String field, String msg) {
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
