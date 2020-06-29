package com.zen.cache;


import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class CommandUsingRequestCache extends HystrixCommand<Boolean> {

    private final int value;

    protected CommandUsingRequestCache(int value) {
        super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
        this.value = value;
    }

    @Override
    protected Boolean run() {
        return value == 0 || value % 2 == 0;
    }

    @Override
    protected String getCacheKey() {
        return String.valueOf(value);
    }

    public static class UnitTest {

        @Test
        public void testWithoutCacheHits() {
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                assertTrue(new CommandUsingRequestCache(2).execute());
                assertFalse(new CommandUsingRequestCache(1).execute());
                assertTrue(new CommandUsingRequestCache(0).execute());
                assertTrue(new CommandUsingRequestCache(58672).execute());
            } finally {
                context.shutdown();
            }
        }

        @Test
        public void testWithCacheHits() {
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                CommandUsingRequestCache command2a = new CommandUsingRequestCache(2);
                CommandUsingRequestCache command2b = new CommandUsingRequestCache(2);

                assertTrue(command2a.execute());
                // this is the first time we've executed this command with the value of "2" so it should not be from cache
                assertFalse(command2a.isResponseFromCache());

                assertTrue(command2b.execute());
                // this is the second time we've executed this command with the same value so it should return from cache
                assertTrue(command2b.isResponseFromCache());
            } finally {
                context.shutdown();
            }

            // start a new request context
            context = HystrixRequestContext.initializeContext();
            try {
                CommandUsingRequestCache command3b = new CommandUsingRequestCache(2);
                assertTrue(command3b.execute());
                // this is a new request context so this should not come from cache
                assertFalse(command3b.isResponseFromCache());
            } finally {
                context.shutdown();
            }
        }


        @Test
        public void testWithCacheHitsInExecutor() {
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                CommandUsingRequestCache command2a = new CommandUsingRequestCache(2);
                assertTrue(command2a.execute());
                assertFalse(command2a.isResponseFromCache());

                ExecutorService executorService = Executors.newSingleThreadExecutor();
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            CommandUsingRequestCache command2b = new CommandUsingRequestCache(2);
                            assertTrue(command2b.execute());
                            assertFalse(command2b.isResponseFromCache());
                        }catch (Throwable e){
                            e.printStackTrace();
                        }
                    }
                });

            } finally {
                context.shutdown();
            }
        }


        @Test
        public void testWithCacheHitsUseHystrixRunnable() {
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                CommandUsingRequestCache command2a = new CommandUsingRequestCache(2);
                assertTrue(command2a.execute());
                assertFalse(command2a.isResponseFromCache());

                ExecutorService executorService = Executors.newSingleThreadExecutor();
                executorService.submit(new HystrixContextRunnable(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            CommandUsingRequestCache command2b = new CommandUsingRequestCache(2);
                            assertTrue(command2b.execute());
                            assertTrue(command2b.isResponseFromCache());
                        }catch (Throwable e){
                            e.printStackTrace();
                        }
                    }
                }));

            } finally {
                context.shutdown();
            }
        }
    }
}
