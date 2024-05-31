package burp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.ScanCheck;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import burpgpt.gpt.GPTRequest;
import burpgpt.gpt.GPTResponse;
import burpgpt.http.GPTClient;
import lombok.Setter;

public class MyScanCheck implements ScanCheck {

    private Logging logging;

    @Setter
    private GPTClient gptClient;

    public MyScanCheck(GPTClient gptClient, Logging logging) {
        this.gptClient = gptClient;
        this.logging = logging;
    }

    @Override
    public AuditResult activeAudit(HttpRequestResponse httpRequestResponse, AuditInsertionPoint auditInsertionPoint) {
        return AuditResult.auditResult(new ArrayList<>());
    }

    @Override
    public AuditResult passiveAudit(HttpRequestResponse httpRequestResponse) {
        try {
            Pair<GPTRequest, GPTResponse> gptResults = gptClient.identifyVulnerabilities(httpRequestResponse);
            List<AuditIssue> auditIssues = createAuditIssuesFromGPTResponse(gptResults, httpRequestResponse);
            return AuditResult.auditResult(auditIssues);
        } catch (IOException e) {
            logging.raiseErrorEvent(e.getMessage());
            return AuditResult.auditResult(new ArrayList<>());
        }
    }

    @Override
    public ConsolidationAction consolidateIssues(AuditIssue newIssue, AuditIssue existingIssue) {
        return newIssue.equals(existingIssue) ? ConsolidationAction.KEEP_EXISTING
                : ConsolidationAction.KEEP_BOTH;
    }

    private List<AuditIssue> createAuditIssuesFromGPTResponse(Pair<GPTRequest, GPTResponse> gptResults,
            HttpRequestResponse httpRequestResponse) {
        List<AuditIssue> auditIssues = new ArrayList<>();
        GPTRequest gptRequest = gptResults.getLeft();
        GPTResponse gptResponse = gptResults.getRight();

        if (gptResponse.getChoices() != null) {
            String escapedPrompt = StringEscapeUtils.escapeHtml4(gptRequest.getPrompt().trim()).replace("\n", "<br />");
            String issueBackground = String.format(
                    "The OpenAI API generated a response using the following parameters:" + "<br>"
                            + "<ul>"
                            + "<li>Model: %s</li>"
                            + "<li>Maximum prompt size: %s</li>"
                            + "<li>Prompt:<br><br>%s</li>"
                            + "</ul>",
                    gptRequest.getModel(), gptRequest.getMaxPromptSize(), escapedPrompt);

            String choiceText = gptResponse.getChoices().get(0).getMessage().getContent();
            String escapedDetail = StringEscapeUtils.escapeHtml4(choiceText.trim()).replace("\n", "<br />");

            AuditIssue auditIssue = AuditIssue.auditIssue(
                    "GPT-generated insights",
                    escapedDetail,
                    null,
                    httpRequestResponse.request().url(),
                    AuditIssueSeverity.INFORMATION,
                    AuditIssueConfidence.TENTATIVE,
                    issueBackground,
                    null,
                    null,
                    httpRequestResponse);
            auditIssues.add(auditIssue);
        }

        return auditIssues;
    }
}
