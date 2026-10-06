package com.fc.v2.common.secre;

import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreOrgBook;

/**
 * 涉密载体入册判定结果（这一处给出的答案）
 *
 * <p>一行立不立得住、还差几件、对上的名录是哪一册，全装在这一份里；
 * 打回去的时候 field 点名毛病落在哪一栏，msg 是说给人听的话。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class SecreVerdict {

    /** 这一行立不立得住 */
    private boolean ok;

    /** 毛病落在哪一栏（needNum/gotNum/bookNo/carrierNo/row），立得住时为空 */
    private String field;

    /** 打回去的话：说明毛病落在哪一栏 */
    private String msg;

    /** 还差几件（这一处当场轧出来的数） */
    private Integer lackNum;

    /** 走名录代号对上的那一册名录 */
    private TSecreOrgBook book;

    /** 落库的那一行（入册成功时回摆，页面上摆的数与回库的数同出一处） */
    private TSecreCarrier row;

    /** 落库行数 */
    private int rows;

    public static SecreVerdict ok(TSecreOrgBook book, Integer lackNum) {
        SecreVerdict v = new SecreVerdict();
        v.ok = true;
        v.book = book;
        v.lackNum = lackNum;
        return v;
    }

    public static SecreVerdict fail(String field, String msg) {
        SecreVerdict v = new SecreVerdict();
        v.ok = false;
        v.field = field;
        v.msg = msg;
        return v;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
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

    public Integer getLackNum() {
        return lackNum;
    }

    public void setLackNum(Integer lackNum) {
        this.lackNum = lackNum;
    }

    public TSecreOrgBook getBook() {
        return book;
    }

    public void setBook(TSecreOrgBook book) {
        this.book = book;
    }

    public TSecreCarrier getRow() {
        return row;
    }

    public void setRow(TSecreCarrier row) {
        this.row = row;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }
}
