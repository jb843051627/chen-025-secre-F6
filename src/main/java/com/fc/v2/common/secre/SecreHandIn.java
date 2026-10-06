package com.fc.v2.common.secre;

import java.math.BigDecimal;

/**
 * 清退移交递来的一行（报文形状）。
 *
 * <p>一行挂两个数：应交几件、核收合格几件，两个数一并递来；缺哪一个，旁人都不许
 * 替它补一个上去。认载体只认件码——报文里那串题名与名录代号只作对照，对不上时
 * 以册面为准，不作落库的凭据。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class SecreHandIn {

    /** 载体件码（认载体只认它，拿题名去凑的不算） */
    private String itemCode;

    /** 本行应交件数 */
    private BigDecimal qty;

    /** 本行核收合格件数 */
    private BigDecimal qualiNum;

    /** 交回渠道（本级送交／上级调回两条来路） */
    private String handKind;

    /** 报文里那串题名（只作对照，落库以册面为凭） */
    private String title;

    /** 报文里那串名录代号（只作对照，落库以册面为凭） */
    private String bookNo;

    public SecreHandIn() {
    }

    public SecreHandIn(String itemCode, BigDecimal qty, BigDecimal qualiNum, String handKind) {
        this.itemCode = itemCode;
        this.qty = qty;
        this.qualiNum = qualiNum;
        this.handKind = handKind;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getQualiNum() {
        return qualiNum;
    }

    public void setQualiNum(BigDecimal qualiNum) {
        this.qualiNum = qualiNum;
    }

    public String getHandKind() {
        return handKind;
    }

    public void setHandKind(String handKind) {
        this.handKind = handKind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBookNo() {
        return bookNo;
    }

    public void setBookNo(String bookNo) {
        this.bookNo = bookNo;
    }
}
