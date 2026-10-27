package com.psych.ai.controller;

import com.psych.ai.common.ApiResponse;
import com.psych.ai.dto.Dtos.AssessmentSubmitReq;
import com.psych.ai.entity.AssessmentRecord;
import com.psych.ai.entity.AssessmentScale;
import com.psych.ai.service.AssessmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    private Long userId(HttpServletRequest req) {
        return (Long) req.getAttribute("userId");
    }

    /** 量表列表 */
    @GetMapping("/scales")
    public ApiResponse<List<AssessmentScale>> scales() {
        return ApiResponse.ok(assessmentService.listScales());
    }

    /** 量表详情（含题目，前端渲染答题页） */
    @GetMapping("/scales/{code}")
    public ApiResponse<AssessmentScale> scale(@PathVariable String code) {
        return ApiResponse.ok(assessmentService.getScale(code));
    }

    /** 提交答卷 → 自动打分 + 生成报告 */
    @PostMapping("/scales/{code}/submit")
    public ApiResponse<AssessmentRecord> submit(@PathVariable String code,
                                                @RequestBody AssessmentSubmitReq body,
                                                HttpServletRequest req) {
        return ApiResponse.ok(assessmentService.submit(userId(req), code, body.answers()));
    }

    /** 我的测评记录 */
    @GetMapping("/records")
    public ApiResponse<List<AssessmentRecord>> records(HttpServletRequest req) {
        return ApiResponse.ok(assessmentService.records(userId(req)));
    }

    /** 记录详情（查看完整报告） */
    @GetMapping("/records/{id}")
    public ApiResponse<AssessmentRecord> record(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok(assessmentService.recordDetail(userId(req), id));
    }
}
