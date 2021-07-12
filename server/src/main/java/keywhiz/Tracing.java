package keywhiz;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.function.Supplier;

public final class Tracing {
  private Tracing() {}

  public static <T> T trace(String spanName, Supplier<T> delegate) {
    Tracer tracer = GlobalTracer.get();

    Span span = tracer.buildSpan(spanName).start();
    try {
      try (Scope ignored = tracer.scopeManager().activate(span)) {
        return delegate.get();
      } catch (Exception e) {
        tagErrors(span, e);
        throw e;
      }
    } finally {
      span.finish();
    }
  }

  public static <T> T tagErrors(Supplier<T> delegate) {
    Span span = GlobalTracer.get().activeSpan();
    try {
      return delegate.get();
    } catch (Exception e) {
      tagErrors(span, e);
      throw e;
    }
  }

  private static void tagErrors(Span span, Exception e) {
    span.setTag(Tags.ERROR, true);

    tagErrorsOpenTracing(span, e);
    tagErrorsDataDog(span, e);
  }

  private static void tagErrorsOpenTracing(Span span, Exception e) {
    https://github.com/opentracing/specification/blob/11dd7f8b16739f02761a9541f04e39f27738f525/semantic_conventions.md#span-and-log-errors

    span.log(
        Map.of(
            Fields.EVENT, "error",
            Fields.ERROR_OBJECT, e
        )
    );
  }

  private static void tagErrorsDataDog(Span span, Exception e) {
    https://github.com/DataDog/dd-trace-java/blob/58f1459a77e342c6cd66f4b6eed75c3deadcc3f3/dd-trace-api/src/main/java/datadog/trace/api/DDTags.java#L17-L19

    span.setTag("error.type", e.getClass().getName());
    span.setTag("error.msg", e.getMessage());
    span.setTag("error.stack", getStackTrace(e));
  }

  private static String getStackTrace(Exception e) {
    StringWriter writer = new StringWriter();
    try (PrintWriter printWriter = new PrintWriter(writer)) {
      e.printStackTrace(printWriter);
    } finally {
      try {
        writer.close();
      } catch (IOException io) {
        throw new RuntimeException(io);
      }
    }
    return writer.toString();
  }

  public static void setTag(String tag, String value) {
    GlobalTracer.get().activeSpan().setTag(tag, value);
  }

  public static void setTag(String tag, Number value) {
    GlobalTracer.get().activeSpan().setTag(tag, value);
  }
}
