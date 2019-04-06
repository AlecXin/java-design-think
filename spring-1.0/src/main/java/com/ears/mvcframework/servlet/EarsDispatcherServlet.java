package com.ears.mvcframework.servlet;

import com.ears.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EarsDispatcherServlet extends HttpServlet {

    // 保存application.json配置文件中的内容
    private Properties contextConfig = new Properties();

    // 保存扫描所有的类名
    private List<String> classNames = new ArrayList<String>();

    // ioc 容器
    private Map<String, Object> ioc = new ConcurrentHashMap<String, Object>();

    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception : " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        Handler handler = getHandler(req);

        if(handler == null) {
            resp.getWriter().write("404 Not Found！！！");
            return;
        }

        Class<?>[] paramTypes = handler.getParamTypes();
        Object[] paramValues = new Object[paramTypes.length];

        Map<String, String[]> params = req.getParameterMap();
        for (Map.Entry<String, String[]> parm : params.entrySet()) {
            String value = Arrays.toString(parm.getValue()).replaceAll("\\[|\\]","")
                    .replaceAll("\\s",",");

            if(!handler.paramIndexMapping.containsKey(parm.getKey())){continue;}

            int index = handler.paramIndexMapping.get(parm.getKey());
            paramValues[index] = convert(paramTypes[index],value);
        }

        if(handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
        }

        if(handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
        }

        Object returnValue = handler.method.invoke(handler.controller,paramValues);
        if(returnValue == null || returnValue instanceof Void){ return; }
        resp.getWriter().write(returnValue.toString());


    }

    private Handler getHandler(HttpServletRequest req) {
        if (handlerMapping.isEmpty()) {return null;}

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();

        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : this.handlerMapping) {
            Matcher matcher = handler.getPattern().matcher(url);
            if(!matcher.matches()) {continue;}
            return handler;
        }
        return null;

    }
    //url传过来的参数都是String类型的，HTTP是基于字符串协议
    //只需要把String转换为任意类型就好
    private Object convert(Class<?> type,String value){
        //如果是int
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        else if(Double.class == type){
            return Double.valueOf(value);
        }
        //如果还有double或者其他类型，继续加if
        //这时候，我们应该想到策略模式了
        //在这里暂时不实现，希望小伙伴自己来实现
        return value;
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1. 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        // 2. 扫描相关的类
        doScanner(contextConfig.getProperty(("scanPackage")));

        // 3. 初始化扫描到的类，并且将他们放到IOC容器中
        doInstance();
        // 4. 完成依赖注入
        doAutowired();
        // 5. 初始化HandlerMapping
        initHandlerMapping();

        System.out.println("Ears Spring framework is init.");
    }

    // 初始化url和method一对一关系
    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            // 判断是否呗EarsController注解
            if (!clazz.isAnnotationPresent(EarsController.class)) {
                return;
            }

            // 获取baseUrl
            String baseUrl = "";
            if (clazz.isAnnotationPresent(EarsRequestMapping.class)) {
                EarsRequestMapping requestMapping = clazz.getAnnotation(EarsRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(EarsRequestMapping.class)) {
                    continue;
                }

                EarsRequestMapping requestMapping = method.getAnnotation(EarsRequestMapping.class);
                String regex = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                this.handlerMapping.add(new Handler(pattern, entry.getValue(), method));

                System.out.println("Mapped : " + pattern + " , " + method);
            }

        }

    }


    private void doAutowired() {
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {

            System.out.println("entry: " + entry.getKey() + " : " + entry.getValue());

            // 获取所有的属性/字段
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(EarsAutowired.class)) {
                    continue;
                }

                // 使用者指定获取的beanName
                EarsAutowired autowired = field.getAnnotation(EarsAutowired.class);

                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }

                // 反射，暴力 强吻,壁咚
                field.setAccessible(true);

                try {
                    // 动态给字段赋值
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.out.println("自动注入完成");

            }

        }


    }


    /**
     * 初始化扫描到的类，并存放到ioc容器中
     */
    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }

        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                // 对@EarsController注解的类进行初始化
                if (clazz.isAnnotationPresent(EarsController.class)) {
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                }
                // 对@Earsservice的类进行初始化
                else if (clazz.isAnnotationPresent(EarsController.class)) {
                    // 获取自定义的beanName
                    EarsService service = clazz.getAnnotation(EarsService.class);
                    // 默认的类名首字母小写
                    String beanName = service.value();
                    if ("".equals(beanName)) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    // 根据类型自动赋值, 投机取巧的方式
                    for (Class<?> clazzInterface : clazz.getInterfaces()) {
                        if (ioc.containsKey(clazzInterface.getName())) {
                            throw new Exception("The " + clazzInterface.getName() + " is exist");
                        }
                        ioc.put(clazzInterface.getName(), instance);
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 将字符串的首字母转成小写
     *
     * @param simpleName
     */
    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 扫描相关的类（被相关注解过的类，例如: @EarsRequestMapping @EarsController）
     * 通过加载配置文件 application.properties ，读取其中的scanPackage 属性信息，得到需要扫描的base package，然后开始文件逐步便利找到全部的className，保存进List<String> classNames容器中
     *
     * @param scanPackage 需要扫描的基础包信息
     */
    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                // 递归便利文件
                doScanner(scanPackage + "." + file.getName());
            } else {
                // 如果该文件不是java的class字节码文件
                if (!file.getName().endsWith(".class")) {
                    continue;
                }

                // 获取class名
                String className = (scanPackage + "." + file.getName()).replace(".class", "");
                classNames.add(className);
            }
        }


    }

    /**
     * 加载 application.properties 配置文件，将其保存至 contextConfig 属性中
     *
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(resourceAsStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != resourceAsStream) {
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class Handler {
        private Pattern pattern;
        private Method method;
        private Object controller;
        private Class<?>[] paramTypes;
        private Map<String, Integer> paramIndexMapping;

        public Handler(Pattern pattern, Object controller, Method method) {
            this.pattern = pattern;
            this.method = method;
            this.controller = controller;

            paramTypes = method.getParameterTypes();

            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();

            // 提取方法中加了注解的参数
            // 把方法上的注解获取得到一个二维数组
            // 因为一个参数可以有多个注解，一个方法又有多个参数
            // 第一层for循环处理参数列表循环
            // 第二层循环处理单个参数的注解数组
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof EarsRequestParam) {
                        String paramName = ((EarsRequestParam) annotation).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                        // 这里没有处理注解参数没有指定参数的注解情况
                    }
                }
            }

            // 处理HttpServletRequest和HttpServletResponse
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if(parameterType == HttpServletRequest.class || parameterType == HttpServletResponse.class) {
                    paramIndexMapping.put(parameterType.getName(), i);
                }
            }

        }

        public Pattern getPattern() {
            return pattern;
        }

        public Method getMethod() {
            return method;
        }

        public Object getController() {
            return controller;
        }

        public Class<?>[] getParamTypes() {
            return paramTypes;
        }
    }
}
