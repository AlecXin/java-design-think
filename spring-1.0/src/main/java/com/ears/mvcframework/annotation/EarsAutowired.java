package com.ears.mvcframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EarsAutowired {
    String value() default  "";
}
