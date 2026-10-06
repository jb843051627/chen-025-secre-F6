package com.fc.v2.common.secre;

/**
 * 单行定去向的判定结果（这一处给出的答案）。
 *
 * <p>定得成定不成、毛病落在哪一栏，全装在这一份里；msg 是说给人听的话。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class SecreHandVerdict {

    /** 这一去定向定不定得成 */
    private boolean ok;

    /** 毛病落在哪一栏（decision/rowNo/status），定得成时为空 */
    private String field;

    /** 说给人听的话 */
    private String msg;

    public static SecreHandVerdict ok(String msg) {
        SecreHandVerdict v = new SecreHandVerdict();
        v.ok = true;
        v.msg = msg;
        return v;
    }

    public static SecreHandVerdict fail(String field, String msg) {
        SecreHandVerdict v = new SecreHandVerdict();
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
}
