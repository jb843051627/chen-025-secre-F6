package com.fc.v2.service;

import java.util.List;

import com.fc.v2.common.secre.SecreHandReceipt;
import com.fc.v2.model.auto.TSecreHandRow;

/**
 * 清退移交行 Service接口（整批进门形状：一册一次递齐，无单条增删改）。
 *
 * <p>整批怎么进门、单行怎么定去向、批次码撞第二遍怎么交代、册首三数从哪里轧出、
 * 旧档那一层沉取——全认判定核那一处给的答案，这里只跑腿。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSecreHandRowService {

    /** 按主键回查单行（沉到旧档的旧账也翻得着） */
    TSecreHandRow selectTSecreHandRowById(Long id);

    /**
     * 一册一次递齐、整册进门。逐行判去向：收下的落地、立不住的只退这一行（缘故写在
     * 这一行上）；越过单册行数上限的整册原样退走、库里不留半行。同一批次码第二遍
     * 只照面：一行不多添、一行不少减，头一遍的去向动不得；要改先撤批。
     *
     * @return 过完门的交代（三数回库逐行数出来，不是先亮在屏上的那份）
     */
    SecreHandReceipt submitBatch(String batchNo, List<TSecreHandRow> rows);

    /**
     * 要回执：回库把这一批的行一行一行数出来。屏上那一份只是复述；同一回算才谈得上
     * 一致。一行都没收着的只写空批，不出收讫。
     */
    SecreHandReceipt receipt(String batchNo);

    /**
     * 撤批：把这一批已落的行撤干净（逻辑删除）。撤干净才谈得上同批次码重递。
     *
     * @return 撤了几行
     */
    int revokeBatch(String batchNo);

    /** 这一批的全部行（现行与退回都在），按行次升序——与机关纸面名册行行对得上 */
    List<TSecreHandRow> listBatchRows(String batchNo);

    /** 现行名单：没沉旧档的行（含已收下与未勾），按批次码、行次升序 */
    List<TSecreHandRow> listCurrentRows(String batchNo);

    /** 旧档那一层：满两个年度没着落沉下去的行，先前收下的旧账仍从批次行里翻得着 */
    List<TSecreHandRow> listArchiveRows(String batchNo);

    /**
     * 沉旧档：去向一直空悬、连着满两个年度还没着落的现行行沉入旧档层。
     * 这条路只朝旧册开；返回沉了几行。
     */
    int sinkExpiredRows(int refYear);
}
