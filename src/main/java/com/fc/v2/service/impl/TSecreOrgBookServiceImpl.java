package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.secre.SecreBookArbiter;
import com.fc.v2.common.secre.SecreSyncReport;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TSecreOrgBookMapper;
import com.fc.v2.model.auto.TSecreOrgBook;
import com.fc.v2.service.ITSecreOrgBookService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 机构名录底册Service业务层处理
 *
 * <p>本机关自录走 {@link #insertTSecreOrgBook}；上级每天下发的那一份走
 * {@link #syncSuperiorBooks}——两头对不上以哪一份作准、冲突怎么点名，
 * 全认判定核那一处给出的答案。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TSecreOrgBookServiceImpl extends ServiceImpl<TSecreOrgBookMapper, TSecreOrgBook> implements ITSecreOrgBookService {

    @Autowired
    private SecreBookArbiter secreBookArbiter;

    @Override
    public TSecreOrgBook selectTSecreOrgBookById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TSecreOrgBook>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TSecreOrgBook> selectTSecreOrgBookList(Wrapper<TSecreOrgBook> queryWrapper) {
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public int insertTSecreOrgBook(TSecreOrgBook record) {
        if (record == null || StringUtils.isEmpty(record.getBookNo())) {
            return 0;
        }
        // 代号是挂行的底：册面已有同代号，这一册立不起来
        Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSecreOrgBook>()
                .eq("book_no", record.getBookNo())
                .eq("del_flag", 0));
        if (dupCnt != null && dupCnt > 0) {
            return 0;
        }
        // 本机关自录：来路一律记本机关自录
        record.setSyncKind(SecreBookArbiter.SYNC_LOCAL);
        if (record.getStatus() == null) {
            record.setStatus(SecreBookArbiter.BOOK_STATUS_ON);
        }
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTSecreOrgBook(TSecreOrgBook record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TSecreOrgBook upd = new TSecreOrgBook();
        upd.setId(record.getId());
        // 名录代号是行挂名录的底，改口不许跟着改代号
        if (record.getBookName() != null) {
            upd.setBookName(record.getBookName());
        }
        if (record.getBookKind() != null) {
            upd.setBookKind(record.getBookKind());
        }
        if (record.getRoadName() != null) {
            upd.setRoadName(record.getRoadName());
        }
        if (record.getStatus() != null) {
            // 单位并走、改了隶属：记为已撤出。撤出只挡新行，不抹旧账
            upd.setStatus(record.getStatus());
        }
        if (record.getRemark() != null) {
            upd.setRemark(record.getRemark());
        }
        upd.setUpdateTime(new Date());
        return this.baseMapper.update(upd, new UpdateWrapper<TSecreOrgBook>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTSecreOrgBookByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTSecreOrgBookById(Long id) {
        return this.baseMapper.deleteById(id);
    }

    @Override
    public SecreSyncReport syncSuperiorBooks(List<TSecreOrgBook> superiorBooks) {
        // 两份名录打架时以哪一份作准，全认这一处给出的答案
        return secreBookArbiter.mergeSuperiorBooks(superiorBooks);
    }
}
