import io.legado.app.help.book.diagnostics.BookSourceDiagnosticsCollector;
import io.legado.app.help.book.diagnostics.DiagnosticErrorClass;
import io.legado.app.help.book.diagnostics.DiagnosticRedactor;
import io.legado.app.help.book.diagnostics.DiagnosticStage;
import io.legado.app.help.book.diagnostics.StageSnapshot;

/**
 * P1 第三轮返工合成探针（只看合成常量，不含任何真实正文/凭据/设备数据）。
 * 在编译产物上直接调用生产类，验证本轮交付与 reviewer-r001 的 F-01/F-02/F-03 处置。
 */
public class P1R3Probe {

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok, String detail) {
        checks++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + " :: " + detail);
        if (!ok) failures++;
    }

    private static BookSourceDiagnosticsCollector bound() {
        BookSourceDiagnosticsCollector collector = new BookSourceDiagnosticsCollector(true);
        collector.bindTarget("https://source.invalid", "https://source.invalid/book/1",
                "https://source.invalid/book/1/2.html");
        return collector;
    }

    public static void main(String[] args) {
        String sentinel = "SYNTHETIC_PRIVATE_BODY_SENTINEL";

        // 阻塞 1：默认异常说明不得引用异常消息（无界输入可能内嵌正文）
        BookSourceDiagnosticsCollector c1 = bound();
        c1.record(DiagnosticStage.EXTRACT, null, 0L,
                new IllegalArgumentException("parse failed near " + sentinel), 0L);
        String lines1 = String.join("\n", c1.summaryLines());
        check("blocker1_exception_message_not_in_summary", !lines1.contains(sentinel), lines1);
        check("blocker1_stable_class_name_present", lines1.contains("IllegalArgumentException"), lines1);

        BookSourceDiagnosticsCollector c2 = bound();
        c2.record(DiagnosticStage.EXTRACT, "SYNTHETIC_BODY", 1L, null, 0L);
        c2.recordFailure(new IllegalStateException("bad rule near " + sentinel));
        String lines2 = String.join("\n", c2.summaryLines());
        check("blocker1_chain_failure_message_not_in_summary", !lines2.contains(sentinel), lines2);

        // 阻塞 2：已执行的真实阶段四快照不得被收尾标注覆盖成「未执行」
        BookSourceDiagnosticsCollector c3 = bound();
        c3.record(DiagnosticStage.READ_PROCESS, "SYNTHETIC_READING", 3L, null, 0L);
        c3.markNotExecutedIfMissing(DiagnosticStage.READ_PROCESS, "书源调试链路止于 BookContent，未进入阅读处理");
        StageSnapshot s3 = c3.snapshotOf(DiagnosticStage.READ_PROCESS);
        check("blocker2_executed_snapshot_kept", s3 != null && s3.getExecuted(), String.valueOf(s3));
        String lines3 = String.join("\n", c3.summaryLines());
        check("blocker2_summary_keeps_real_reading_readout",
                lines3.contains("4.阅读处理后") && !lines3.contains("未执行"), lines3);

        BookSourceDiagnosticsCollector c4 = bound();
        c4.markNotExecutedIfMissing(DiagnosticStage.READ_PROCESS, "未进入阅读处理");
        StageSnapshot s4 = c4.snapshotOf(DiagnosticStage.READ_PROCESS);
        check("blocker2_missing_stage_still_marked", s4 != null && !s4.getExecuted(), String.valueOf(s4));

        // F-01：抓取链路的失败不得贴到阅读路径产生的阶段四读数上
        BookSourceDiagnosticsCollector c5 = bound();
        c5.record(DiagnosticStage.READ_PROCESS, "SYNTHETIC_READING", 3L, null, 0L);
        c5.recordFailure(new java.net.UnknownHostException("source.invalid"));
        StageSnapshot s5r = c5.snapshotOf(DiagnosticStage.READ_PROCESS);
        StageSnapshot s5e = c5.snapshotOf(DiagnosticStage.EXTRACT);
        boolean f01 = s5r != null && s5r.getExecuted()
                && s5r.getErrorClass() == DiagnosticErrorClass.NONE
                && s5e != null && s5e.getErrorClass() == DiagnosticErrorClass.NETWORK;
        check("f01_chain_failure_not_stamped_on_reading_stage", f01,
                String.valueOf(s5r) + " | " + String.valueOf(s5e));

        // F-02：展示时机判定三态（无读数 / 只有未执行标注 / 首次真实读数 + 去重）
        BookSourceDiagnosticsCollector c6 = bound();
        boolean noSnapshot = c6.shouldReportReadingStage();
        c6.markNotExecutedIfMissing(DiagnosticStage.READ_PROCESS, "未进入阅读处理");
        boolean onlyNotExecuted = c6.shouldReportReadingStage();
        c6.record(DiagnosticStage.READ_PROCESS, "SYNTHETIC_READING", 3L, null, 0L);
        boolean firstReal = c6.shouldReportReadingStage();
        boolean secondReal = c6.shouldReportReadingStage();
        check("f02_report_decision_three_states",
                !noSnapshot && !onlyNotExecuted && firstReal && !secondReal,
                "noSnapshot=" + noSnapshot + ",onlyNotExecuted=" + onlyNotExecuted
                        + ",firstReal=" + firstReal + ",secondReal=" + secondReal);

        // F-03：路径里的 '@' 不能把主机名切掉；真 userinfo 仍要丢弃；畸形 URL 保守回退
        String sanitized = DiagnosticRedactor.INSTANCE.sanitizeMessage("请求 https://api.example.com/@user/profile 失败");
        check("f03_host_kept_when_path_has_at",
                sanitized.equals("请求 https://api.example.com/@user/profile 失败"), sanitized);
        String sanitized2 = DiagnosticRedactor.INSTANCE.sanitizeMessage("请求 https://user:pw@api.example.com/@user/profile 失败");
        check("f03_userinfo_still_dropped",
                !sanitized2.contains("pw@") && sanitized2.contains("https://api.example.com/@user/profile"), sanitized2);
        String sanitized3 = DiagnosticRedactor.INSTANCE.sanitizeMessage("请求 https:///user:pw@api.example.com/x 失败");
        check("f03_malformed_url_without_authority_drops_credentials",
                !sanitized3.contains("pw") && sanitized3.contains("api.example.com/x"), sanitized3);

        System.out.println("probe_failures=" + failures + "/" + checks);
        System.exit(failures == 0 ? 0 : 1);
    }
}
