package com.ears.demo.service.impl;

import com.ears.mvcframework.annotation.EarsService;
import com.ears.demo.service.IDemoService;

/**
 * 核心业务逻辑
 */
@EarsService
public class DemoService implements IDemoService{

	public String get(String name) {
		return "My name is " + name;
	}

}
