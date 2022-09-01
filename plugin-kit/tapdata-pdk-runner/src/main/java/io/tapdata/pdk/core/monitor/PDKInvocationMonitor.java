package io.tapdata.pdk.core.monitor;

import io.tapdata.entity.logger.TapLogger;
import io.tapdata.entity.error.CoreException;
import io.tapdata.entity.utils.ParagraphFormatter;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.pdk.apis.functions.ConnectorFunctions;
import io.tapdata.pdk.apis.functions.PDKMethod;
import io.tapdata.pdk.apis.functions.connection.ErrorHandleFunction;
import io.tapdata.pdk.apis.functions.connection.RetryOptions;
import io.tapdata.pdk.apis.functions.connector.TapFunction;
import io.tapdata.pdk.apis.functions.connector.target.AlterDatabaseTimeZoneFunction;
import io.tapdata.pdk.core.api.ConnectionNode;
import io.tapdata.pdk.core.api.ConnectorNode;
import io.tapdata.pdk.core.api.Node;
import io.tapdata.pdk.core.entity.params.PDKMethodInvoker;
import io.tapdata.pdk.core.error.PDKRunnerErrorCodes;
import io.tapdata.pdk.core.executor.ExecutorsManager;
import io.tapdata.pdk.core.memory.MemoryFetcher;
import io.tapdata.pdk.core.utils.CommonUtils;
import net.sf.cglib.beans.BulkBeanException;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * TODO start monitor thread for checking slow invocation
 */
public class PDKInvocationMonitor implements MemoryFetcher {
    private static final String TAG = PDKInvocationMonitor.class.getSimpleName();
    private static volatile PDKInvocationMonitor instance = new PDKInvocationMonitor();
    private static final Object lock = new int[0];

    private Map<PDKMethod, InvocationCollector> methodInvocationCollectorMap = new ConcurrentHashMap<>();

    private Consumer<String> errorListener;

    private PDKInvocationMonitor() {}

    public void setErrorListener(Consumer<String> errorListener) {
        this.errorListener = errorListener;
    }

    public static PDKInvocationMonitor getInstance() {
        return instance;
    }

    public static void invoke(Node node, PDKMethod method, CommonUtils.AnyError r, String logTag, Consumer<CoreException> errorConsumer) {
        instance.invokePDKMethod(node, method, r, null, logTag, errorConsumer, false, 0, 0);
    }
    public static void invoke(Node node, PDKMethod method, CommonUtils.AnyError r, String logTag) {
        instance.invokePDKMethod(node, method, r, null, logTag);
    }
    public static void invoke(Node node, PDKMethod method, CommonUtils.AnyError r, String message, String logTag) {
        instance.invokePDKMethod(node, method, r, message, logTag, null, false, 0, 0);
    }
    public static void invoke(Node node, PDKMethod method, CommonUtils.AnyError r, String message, String logTag, Consumer<CoreException> errorConsumer) {
        instance.invokePDKMethod(node, method, r, message, logTag, errorConsumer, false, 0, 0);
    }
    public static void invoke(Node node, PDKMethod method, CommonUtils.AnyError r, String message, final String logTag, Consumer<CoreException> errorConsumer, boolean async, long retryTimes, long retryPeriodSeconds) {
        instance.invokePDKMethod(node, method, r, message, logTag, errorConsumer, async, null, retryTimes, retryPeriodSeconds);
    }
    public static void invoke(Node node, PDKMethod method, CommonUtils.AnyError r, String message, final String logTag, Consumer<CoreException> errorConsumer, boolean async, ClassLoader contextClassLoader, long retryTimes, long retryPeriodSeconds) {
        instance.invokePDKMethod(node, method, r, message, logTag, errorConsumer, async, contextClassLoader, retryTimes, retryPeriodSeconds);
    }

    public static void invoke(Node node, PDKMethod method, PDKMethodInvoker invoker){
        instance.invokePDKMethod(node, method, invoker);
    }

    public void invokePDKMethod(Node node, PDKMethod method, CommonUtils.AnyError r, String logTag) {
        invokePDKMethod(node, method, r, null, logTag);
    }
    public void invokePDKMethod(Node node, PDKMethod method, CommonUtils.AnyError r, String message, String logTag) {
        invokePDKMethod(node, method, r, message, logTag, null, false, 0, 0);
    }
    public void invokePDKMethod(Node node, PDKMethod method, CommonUtils.AnyError r, String message, String logTag, Consumer<CoreException> errorConsumer) {
        invokePDKMethod(node, method, r, message, logTag, errorConsumer, false, 0, 0);
    }
    public void invokePDKMethod(Node node, PDKMethod method, CommonUtils.AnyError r, String message, final String logTag, Consumer<CoreException> errorConsumer, boolean async, long retryTimes, long retryPeriodSeconds) {
        invokePDKMethod(node, method, r, message, logTag, errorConsumer, async, null, retryTimes, retryPeriodSeconds);
    }
    public void invokePDKMethod(Node node, PDKMethod method, PDKMethodInvoker invoker){
        invokePDKMethod(node,method,invoker.getR(),invoker.getMessage(),invoker.getLogTag(),invoker.getErrorConsumer(),invoker.isAsync(),invoker.getContextClassLoader(),invoker.getRetryTimes(),invoker.getRetryPeriodSeconds());
    }
    public void invokePDKMethod(Node node, PDKMethod method, CommonUtils.AnyError r, String message, final String logTag, Consumer<CoreException> errorConsumer, boolean async, ClassLoader contextClassLoader, long retryTimes, long retryPeriodSeconds) {
        if(async) {
            ExecutorsManager.getInstance().getExecutorService().execute(() -> {
                if(contextClassLoader != null)
                    Thread.currentThread().setContextClassLoader(contextClassLoader);
                if(retryTimes > 0) {
                    CommonUtils.autoRetryAsync(() ->
                            node.applyClassLoaderContext(() ->
                                    invokePDKMethodPrivate(method, r, message, logTag, errorConsumer)), logTag, message, retryTimes, retryPeriodSeconds);
                } else {
                    node.applyClassLoaderContext(() -> invokePDKMethodPrivate(method, r, message, logTag, errorConsumer));
                }
            });
        } else {
            node.applyClassLoaderContext(() -> invokePDKMethodPrivate(method, r, message, logTag, errorConsumer));
        }
    }
    public void invokePDKMethod(Node node, PDKMethod method, CommonUtils.AnyError r, String message, final String logTag, Consumer<CoreException> errorConsumer, boolean async, ClassLoader contextClassLoader, long retryTimes, long retryPeriodSeconds,Runnable beforeRetryMethod,boolean needRetry) {
        if(async) {
            ExecutorsManager.getInstance().getExecutorService().execute(() -> {
                if(contextClassLoader != null) {
                    Thread.currentThread().setContextClassLoader(contextClassLoader);
                }
                if(retryTimes > 0) {
                    CommonUtils.autoRetry(new CommonUtils.AutoRetryParams()
                            .async(async)
                            .message(message)
                            .times(new AtomicLong(retryTimes))
                            .periodSeconds(retryPeriodSeconds)
                            .tag(logTag)
                            .node(node)
                            .runnable(() -> node.applyClassLoaderContext(() -> invokePDKMethodPrivate(method, r, message, logTag, errorConsumer) ))
                    );
                } else {
                    node.applyClassLoaderContext(() -> invokePDKMethodPrivate(method, r, message, logTag, errorConsumer));
                }
            });
        } else {
            CommonUtils.autoRetry(new CommonUtils.AutoRetryParams()
                    .async(async)
                    .message(message)
                    .times(new AtomicLong(retryTimes))
                    .periodSeconds(retryPeriodSeconds)
                    .tag(logTag)
                    .node(node)
                    .runnable(() -> node.applyClassLoaderContext(() -> invokePDKMethodPrivate(method, r, message, logTag, errorConsumer)))
            );
        }
    }
    private void invokePDKMethodPrivate(PDKMethod method, CommonUtils.AnyError r, String message, String logTag, Consumer<CoreException> errorConsumer) {
        String invokeId = methodStart(method, logTag);
        Throwable theError = null;
        try {
            r.run();
        } catch(CoreException coreException) {
            theError = coreException;

            if(errorConsumer != null) {
                errorConsumer.accept(coreException);
            } else {
                if(errorListener != null)
                    errorListener.accept(describeError(method, coreException, message, logTag));
                throw coreException;
            }
        } catch(Throwable throwable) {
            throwable.printStackTrace();
            theError = throwable;

            CoreException coreException = new CoreException(PDKRunnerErrorCodes.COMMON_UNKNOWN, throwable.getMessage(), throwable);
            if(errorConsumer != null) {
                errorConsumer.accept(coreException);
            } else {
                if(errorListener != null)
                    errorListener.accept(describeError(method, throwable, message, logTag));
                throw coreException;
            }
        } finally {
            methodEnd(method, invokeId, theError, message, logTag);
        }
    }

    private String describeError(PDKMethod method, Throwable throwable, String message, String logTag) {
        return logTag + ": Invoke PDKMethod " + method.name() + " failed, error " + throwable.getMessage() + " context message " + message;
    }

    public String methodStart(PDKMethod method, String logTag) {
        final String invokeId = CommonUtils.processUniqueId();
        InvocationCollector collector = methodInvocationCollectorMap.computeIfAbsent(method, InvocationCollector::new);
        collector.getInvokeIdTimeMap().put(invokeId, System.currentTimeMillis());
//        TapLogger.info(logTag, "methodStart {} invokeId {}", method, invokeId);
        return invokeId;
    }

    public Long methodEnd(PDKMethod method, String invokeId, Throwable error, String message, String logTag) {
        InvocationCollector collector = methodInvocationCollectorMap.get(method);
        if(collector != null) {
            Long time = collector.getInvokeIdTimeMap().remove(invokeId);
            if(time != null) {
                collector.getCounter().increment();
                long takes = System.currentTimeMillis() - time;
                collector.getTotalTakes().add(takes);
                if(error != null) {
                    TapLogger.error(logTag, "methodEnd {} invokeId {} failed, message {} takes {} error {}", method, invokeId, message, takes, ExceptionUtils.getStackTrace(error));
                } else {
//                    TapLogger.info(logTag, "methodEnd {} invokeId {} successfully, message {} takes {}", method, invokeId, message, takes);
                }
                return takes;
            }
        }
        return null;
    }

    public static void main(String... args) {
        long time = System.currentTimeMillis();
        for(int i = 0; i < 1000000; i++) {
            UUID.randomUUID().toString();
        }
        System.out.println("takes " + (System.currentTimeMillis() - time));

        AtomicLong counter = new AtomicLong(0);
        time = System.currentTimeMillis();
        String id = null;
        for(int i = 0; i < 1000000; i++) {
            id = Long.toHexString(System.currentTimeMillis()) + Long.toHexString(counter.getAndIncrement());
        }
        System.out.println("takes " + (System.currentTimeMillis() - time) + " id " + id);
    }

    @Override
    public String memory(List<String> mapKeys, String memoryLevel) {
        ParagraphFormatter paragraphFormatter = new ParagraphFormatter(PDKInvocationMonitor.class.getSimpleName());
        for(Map.Entry<PDKMethod, InvocationCollector> entry : methodInvocationCollectorMap.entrySet()) {
            if(mapKeys != null && !mapKeys.isEmpty() && !mapKeys.contains(entry.getKey().name()))
                continue;
            paragraphFormatter.addRow(entry.getKey().name(), entry.getValue().toMemoryString(memoryLevel));
        }
        return paragraphFormatter.toString();
    }
}
