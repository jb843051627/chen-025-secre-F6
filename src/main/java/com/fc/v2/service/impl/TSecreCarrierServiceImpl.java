package com.fc.v2.service.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.secre.SecreBookArbiter;
import com.fc.v2.common.secre.SecreVerdict;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TSecreCarrierMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreOrgBook;
import com.fc.v2.service.ITSecreCarrierService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 涉密载体册面Service业务层处理
 *
 * <p>立行、轧差、视野、名录打架的判路全在 {@link SecreBookArbiter} 这一处，
 * 这里只跑腿：对册、落库、把这一处轧出来的数写回。还差几件那一格不由人手填，
 * 也不由报文说了算——头一回入册要轧，已收件数改口要轧，换了挂靠的名录也要轧，
 * 落库那一刻一律按这一行自己的两个数重轧一遍。</p>
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TSecreCarrierServiceImpl extends ServiceImpl<TSecreCarrierMapper, TSecreCarrier> implements ITSecreCarrierService {

    @Autowired
    private SecreBookArbiter secreBookArbiter;

    @Override
    public TSecreCarrier selectTSecreCarrierById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TSecreCarrier>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TSecreCarrier> selectTSecreCarrierList(Wrapper<TSecreCarrier> queryWrapper) {
        // 视野与条件由调用方（经判定核）组好，这里照单全收——库里入过的每一行都寻得着
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public int insertTSecreCarrier(TSecreCarrier record) {
        // 旧口维持原契约：成立回 1，立不起来回 0；判路全部走判定核
        SecreVerdict verdict = registerTSecreCarrier(record);
        return verdict.isOk() ? verdict.getRows() : 0;
    }

    @Override
    public SecreVerdict registerTSecreCarrier(TSecreCarrier record) {
        if (record == null) {
            return SecreVerdict.fail("row", "入册那一行空着，这一行立不起来");
        }
        // 这一行立不立得住，全认这一处的答案
        SecreVerdict verdict = secreBookArbiter.judgeNewRow(record);
        if (!verdict.isOk()) {
            return verdict;
        }
        // 行与名录的关联一律走名录代号：以代号对出的名录回填，不拿主键挂行
        TSecreOrgBook book = verdict.getBook();
        record.setBookNo(book.getBookNo());
        record.setBookId(fitBookId(book));
        if (StringUtils.isNotEmpty(record.getCarrierNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSecreCarrier>()
                    .eq("carrier_no", record.getCarrierNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return SecreVerdict.fail("carrierNo", "涉密载体件码一栏与册面已有行重复，这一行立不起来");
            }
        }
        // 还差几件：只认这一处当场轧出来的数，报文里带了多少一律不作数
        record.setLackNum(verdict.getLackNum());
        record.setDelFlag(0);
        int rows = this.baseMapper.insert(record);
        if (rows <= 0) {
            return SecreVerdict.fail("row", "落库未成，这一行立不起来");
        }
        verdict.setRows(rows);
        verdict.setRow(record);
        return verdict;
    }

    @Override
    public int updateTSecreCarrier(TSecreCarrier record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TSecreCarrier db = this.baseMapper.selectOne(new QueryWrapper<TSecreCarrier>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
        if (db == null) {
            return 0;
        }
        // 换了挂靠的名录，按新挂判（已撤出的名录只挡新挂，不抹旧账）
        boolean bookChanged = record.getBookNo() != null && !record.getBookNo().equals(db.getBookNo());
        // 改口并账：来报里带来的栏盖到库里的行上，再拿去判
        TSecreCarrier merged = mergeRow(db, record);
        SecreVerdict verdict = secreBookArbiter.judgeRow(merged, bookChanged);
        if (!verdict.isOk()) {
            return 0;
        }
        // 件码改了口才查重：册面已有同码的别行，这一改立不起来
        if (StringUtils.isNotEmpty(record.getCarrierNo()) && !record.getCarrierNo().equals(db.getCarrierNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSecreCarrier>()
                    .eq("carrier_no", record.getCarrierNo())
                    .ne("id", record.getId())
                    .eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        // 写回：带来的栏 + 这一处重轧出来的差；页面上摆的数与回库的数同出一处
        TSecreCarrier upd = new TSecreCarrier();
        upd.setId(record.getId());
        if (record.getCarrierNo() != null) {
            upd.setCarrierNo(record.getCarrierNo());
        }
        if (record.getPostNo() != null) {
            upd.setPostNo(record.getPostNo());
        }
        if (record.getNeedNum() != null) {
            upd.setNeedNum(record.getNeedNum());
        }
        if (record.getGotNum() != null) {
            upd.setGotNum(record.getGotNum());
        }
        if (record.getContent() != null) {
            upd.setContent(record.getContent());
        }
        if (record.getStatus() != null) {
            upd.setStatus(record.getStatus());
        }
        if (record.getRemark() != null) {
            upd.setRemark(record.getRemark());
        }
        if (bookChanged) {
            TSecreOrgBook book = verdict.getBook();
            upd.setBookNo(book.getBookNo());
            upd.setBookId(fitBookId(book));
        }
        upd.setLackNum(verdict.getLackNum());
        upd.setUpdateTime(new Date());
        return this.baseMapper.update(upd, new UpdateWrapper<TSecreCarrier>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTSecreCarrierByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTSecreCarrierById(Long id) {
        return this.baseMapper.deleteById(id);
    }

    /**
     * 改口并账：来报里非空的栏盖到库里的行上，得出要判的那一行。
     */
    private TSecreCarrier mergeRow(TSecreCarrier db, TSecreCarrier record) {
        TSecreCarrier merged = new TSecreCarrier();
        merged.setId(db.getId());
        merged.setCarrierNo(record.getCarrierNo() != null ? record.getCarrierNo() : db.getCarrierNo());
        merged.setBookNo(record.getBookNo() != null ? record.getBookNo() : db.getBookNo());
        merged.setPostNo(record.getPostNo() != null ? record.getPostNo() : db.getPostNo());
        merged.setNeedNum(record.getNeedNum() != null ? record.getNeedNum() : db.getNeedNum());
        merged.setGotNum(record.getGotNum() != null ? record.getGotNum() : db.getGotNum());
        merged.setContent(record.getContent() != null ? record.getContent() : db.getContent());
        merged.setStatus(record.getStatus() != null ? record.getStatus() : db.getStatus());
        merged.setRemark(record.getRemark() != null ? record.getRemark() : db.getRemark());
        return merged;
    }

    /**
     * 名录主键只是冗余回填：装得进册面 book_id 那一栏（int）才填，关联一律以代号为准。
     */
    private Integer fitBookId(TSecreOrgBook book) {
        Long id = book.getId();
        if (id == null || id > Integer.MAX_VALUE || id < Integer.MIN_VALUE) {
            return null;
        }
        return id.intValue();
    }
}
