package ti4.website;

import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import ti4.logging.SlowApiCallWarning;

class SlowS3CallInterceptor implements ExecutionInterceptor {

    private static final ExecutionAttribute<Long> START_NANOS = new ExecutionAttribute<>("SlowS3CallStartNanos");

    @Override
    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
        executionAttributes.putAttribute(START_NANOS, System.nanoTime());
    }

    @Override
    public void afterExecution(Context.AfterExecution context, ExecutionAttributes executionAttributes) {
        warnIfSlow(
                context.request(),
                executionAttributes,
                "status " + context.httpResponse().statusCode());
    }

    @Override
    public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes executionAttributes) {
        warnIfSlow(
                context.request(),
                executionAttributes,
                "exception " + context.exception().getClass().getSimpleName());
    }

    private static void warnIfSlow(SdkRequest request, ExecutionAttributes executionAttributes, String outcome) {
        Long startNanos = executionAttributes.getAttribute(START_NANOS);
        if (startNanos == null) return;
        SlowApiCallWarning.warnIfSlowOutgoing(describe(request, executionAttributes), outcome, startNanos);
    }

    private static String describe(SdkRequest request, ExecutionAttributes executionAttributes) {
        String operation = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
        String bucket = request.getValueForField("Bucket", String.class).orElse("?");
        String key = request.getValueForField("Key", String.class).orElse("");
        return "S3 " + operation + " " + bucket + "/" + key;
    }
}
