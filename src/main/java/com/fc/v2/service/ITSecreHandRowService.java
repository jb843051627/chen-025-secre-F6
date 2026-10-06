package com.fc.v2.service;

import java.time.LocalDate;
import java.util.List;

import com.fc.v2.common.secre.SecreHandIn;
import com.fc.v2.common.secre.SecreHandReceipt;
import com.fc.v2.common.secre.SecreHandVerdict;
import com.fc.v2.model.auto.TSecreHandRow;

/**
 * 清退移交行 Service接口（batch-process 形状：整册进门，无单条增删改）
 *
 * <p>整批怎么进门、单行怎么定去向、同一个批次码撞上第二遍怎么交代、册首三数从
 * 哪里轧出，这四问只认 {@link com.fc.v2.common.secre.SecreHandGate} 这一处给出的
 * 答案；这里只跑腿递话，不另起判路。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITSecreHandRowService {

    /** 按主键回查单行 */
    TSecreHandRow selectTSecreHandRowById(Long id);

    /**
     * 整批进门：一册一次递齐、整册进门，不接逐行零着递。
     * 行数越过单册上限的整册原样退走，库里不许留半行；同一批次码再递一遍只照面
     * 不翻账。回执三个数（原报、落地、退回）回库一行一行数出来。
     */
    SecreHandReceipt enterBatch(String batchNo, List<SecreHandIn> rows);

    /** 回执三数：回库一行一行数出来（屏上先亮的那份只是这一份的复述） */
    SecreHandReceipt receiptOf(String batchNo);

    /** 随手点一行去核：行上的去向与回执三个数互证互对，对不住的点名报出去 */
    SecreHandReceipt verify(String batchNo);

    /**
     * 单行定去向：未勾的行勾成收下或退回（只许这两个字样）。
     * 头一遍落成的去向动它不得；真要改，先办撤批，撤干净才谈得上重递。
     */
    SecreHandVerdict decideRow(String batchNo, Integer rowNo, int decision, String reason);

    /** 撤批：这一册从头撤干净，才谈得上重递；返回撤下几行 */
    int cancelBatch(String batchNo);

    /** 旧档沉取：去向空悬连着满两个年度的行沉入旧档；返回这一回沉下去几行 */
    int sinkStale(LocalDate today);

    /** 现行名单：这一册还在现行这一层的行（按纸面行次排） */
    List<TSecreHandRow> listCurrent(String batchNo);

    /** 旧档那一层：这一册已沉下去的行（按纸面行次排，一条不少） */
    List<TSecreHandRow> listArchive(String batchNo);
}
