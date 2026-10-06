package com.fc.v2.common.secre;

/**
 * 名录下发对不上的一条冲突（逐条点名，点到代号）
 *
 * @author fuce
 * @date 2026-10-06
 */
public class SecreSyncConflict {

    /** 对不上的名录代号 */
    private String bookNo;

    /** 对不上的缘由（哪一栏对不上、两头各是什么、以哪一份作准） */
    private String reason;

    public SecreSyncConflict() {
    }

    public SecreSyncConflict(String bookNo, String reason) {
        this.bookNo = bookNo;
        this.reason = reason;
    }

    public String getBookNo() {
        return bookNo;
    }

    public void setBookNo(String bookNo) {
        this.bookNo = bookNo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
