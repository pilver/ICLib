package com.risch.evertsson.iclib.com.risch.evertsson.iclib.icdispatch;

import android.os.Handler;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by johanrisch on 6/18/13.
 */
public class ICDispatch {
    /**
     * The normal priority thread.
     */
    private static ICThread sNormalThread;
    /**
     * The low priority thread
     */
    private static ICThread sLowThread;
    /**
     * the high priority thread.
     */
    private static ICThread sHighThread;

    /**
     * Static int telling {@see ICDispatch} to execute on the high priority thread
     * Constant value = 0
     */
    public static final int HIGH = 0;
    /**
     * Static int telling {@see ICDispatch} to execute on the normal priority thread
     * Constant value = 1
     */
    public static final int NORMAL = 1;
    /**
     * Static int telling {@see ICDispatch} to execute on the low priority thread
     * Constant value = 2
     */
    public static final int LOW = 2;
    /**
     * Static int telling {@see ICDispatch} to execute on the UI thread
     * Constant value = 3
     */
    public static final int MAIN = 3;
    /**
     * Handler used to execute on UI thread.
     */
    private Handler sMainHandler;
    /**
     * Map used to cache method mappings.
     */
    private HashMap<String, Method> mMethodMap;

    /**
     * initiates ICDispatch. MUST be called at before scheduling blocks.
     */
    public void initICDispatch() {

        sHighThread = new ICThread(new LinkedBlockingQueue<ICBlock>());
        sHighThread.setPriority(Thread.MAX_PRIORITY);
        sHighThread.start();

        sMainHandler = new Handler();

        sNormalThread = new ICThread(new LinkedBlockingQueue<ICBlock>());
        sNormalThread.setPriority(Thread.NORM_PRIORITY);
        sNormalThread.start();

        sLowThread = new ICThread(new LinkedBlockingQueue<ICBlock>());
        sLowThread.setPriority(Thread.MIN_PRIORITY);
        sLowThread.start();

        mMethodMap = new HashMap<String, Method>(250);
    }

    /**
     * Schedules a block for execution on the given queue.
     *
     * @param block the block to be executed on the chosen queue
     * @param queue the queue to execute the supplied block on.
     * @return true if the enqueueing was successfull.
     * @throws RuntimeException if the supplied queue does not exist.
     */
    public boolean executeOn(int queue, final ICBlock block) {
        Log.d("ICDispatch", "scheduling execution of a block on " + queue);
        switch (queue) {
            case HIGH:
                sHighThread.putBlock(block);
                break;
            case NORMAL:
                sNormalThread.putBlock(block);
                break;
            case LOW:
                sLowThread.putBlock(block);
                break;
            case MAIN:
                sMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        block.doAction();
                    }
                });
                break;
            default:
                throw new RuntimeException("Invalid thread ID, " + queue +
                        " please supply one of LOW, NORMAL, HIGH or MAIN");
        }
        return true;
    }

    /**
     * Schedules a method for execution on the supplied thread.
     * ICDispatch keeps an internal Hashmap of method name and containing class in order to speed up consecutive
     * calls to the method. At the moment polymorphism is not supported.
     * @param queue that the method execution should be put in.
     * @param instance the instance of the object that the method should be executed on.
     * @param name the name of the method to execute.
     * @param args the arguments for the method.
     * @return true if the execution was successful.
     */
    public boolean executeMethodOn(int queue, final Object instance, String name, final Object... args) {
        try {
            Method m = mMethodMap.get(instance.getClass().getName()+name);
            if (m == null) {
                //Method is not cached find the method...
                //TODO- handle case when hashmap of methods is to large.
                Class[] classes = null;
                if (args.length > 0) {
                    classes = new Class[args.length];
                    for (int i = 0; i < classes.length; i++) {
                        classes[i] = args[i].getClass();
                    }
                }
                m = instance.getClass().getMethod(name, classes);

                mMethodMap.put(instance.getClass().getName()+name, m);
            }
            final Method finalM = m;
            executeOn(queue, new ICBlock() {
                @Override
                public void doAction() {
                    try {
                        finalM.invoke(instance, args);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            });


        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
