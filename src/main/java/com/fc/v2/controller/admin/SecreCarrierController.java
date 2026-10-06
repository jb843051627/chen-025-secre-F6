package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.common.secre.SecreBookArbiter;
import com.fc.v2.common.secre.SecreVerdict;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.service.ITSecreCarrierService;
import com.fc.v2.util.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 涉密载体册面 Controller
 *
 * <p>旧口（add/edit/remove/list）收什么回什么一律维持原状；想换一种入册姿势的，
 * 走同义的 {@link #register(TSecreCarrier)}——打回时点名毛病落在哪一栏。
 * 这一屏该见哪几行，全认判定核划出来的视野。</p>
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "涉密载体册面")
@Controller
@RequestMapping("/SecreCarrierController")
public class SecreCarrierController extends BaseController {

    private final String prefix = "admin/secreCarrier";

    @Autowired
    private ITSecreCarrierService secreCarrierService;

    @Autowired
    private SecreBookArbiter secreBookArbiter;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("secre:secreCarrier:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "涉密载体册面集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("secre:secreCarrier:list")
    @ResponseBody
    public ResultTable list(TSecreCarrier record, String roadName, String bookNos) {
        // 这一屏该见哪几行：本级按属地圈（一直写到街道），市保密办按划给的那几家圈
        QueryWrapper<TSecreCarrier> queryWrapper =
                secreBookArbiter.scopeWrapper(roadName, splitBookNos(bookNos));
        if (record != null) {
            if (StringUtils.isNotEmpty(record.getCarrierNo())) {
                queryWrapper.eq("carrier_no", record.getCarrierNo());
            }
            if (StringUtils.isNotEmpty(record.getBookNo())) {
                queryWrapper.eq("book_no", record.getBookNo());
            }
            if (StringUtils.isNotEmpty(record.getPostNo())) {
                queryWrapper.eq("post_no", record.getPostNo());
            }
            if (record.getStatus() != null) {
                queryWrapper.eq("status", record.getStatus());
            }
        }
        startPage();
        com.github.pagehelper.PageInfo<TSecreCarrier> page =
                new com.github.pagehelper.PageInfo<TSecreCarrier>(secreCarrierService.selectTSecreCarrierList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "涉密载体册面新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("secre:secreCarrier:add")
    @ResponseBody
    public AjaxResult add(TSecreCarrier record) {
        return toAjax(secreCarrierService.insertTSecreCarrier(record));
    }

    @Log(title = "涉密载体册面入册", action = "register")
    @ApiOperation(value = "入册（同义新口）", notes = "与add同义；打回时点名毛病落在哪一栏")
    @PostMapping("/register")
    @RequiresPermissions("secre:secreCarrier:add")
    @ResponseBody
    public AjaxResult register(TSecreCarrier record) {
        SecreVerdict verdict = secreCarrierService.registerTSecreCarrier(record);
        if (!verdict.isOk()) {
            AjaxResult error = AjaxResult.error(verdict.getMsg());
            error.put("field", verdict.getField());
            return error;
        }
        // 页面上摆出来的还差几件，与回库逐行点出来的那一遍，同是这一处轧的账
        AjaxResult ok = AjaxResult.success("入册成功");
        ok.put("data", verdict.getRow());
        ok.put("lackNum", verdict.getLackNum());
        return ok;
    }

    @Log(title = "涉密载体册面修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("secre:secreCarrier:edit")
    @ResponseBody
    public AjaxResult editSave(TSecreCarrier record) {
        return toAjax(secreCarrierService.updateTSecreCarrier(record));
    }

    @Log(title = "涉密载体册面删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("secre:secreCarrier:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(secreCarrierService.deleteTSecreCarrierByIds(ids));
    }

    /** 划给这一屏的名录代号串（逗号隔开）；没传就是 null——不按名录圈 */
    private List<String> splitBookNos(String bookNos) {
        if (StringUtils.isEmpty(bookNos)) {
            return null;
        }
        return Arrays.stream(bookNos.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
