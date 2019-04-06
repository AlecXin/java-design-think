package com.ears.demo.mvc.action;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ears.demo.service.IDemoService;
import com.ears.mvcframework.annotation.EarsAutowired;
import com.ears.mvcframework.annotation.EarsController;
import com.ears.mvcframework.annotation.EarsRequestMapping;
import com.ears.mvcframework.annotation.EarsRequestParam;


//虽然，用法一样，但是没有功能
@EarsController
@EarsRequestMapping("/demo")
public class DemoAction {

  	@EarsAutowired private IDemoService demoService;

	@EarsRequestMapping("/query.*")
	public void query(HttpServletRequest req, HttpServletResponse resp,
					  @EarsRequestParam("name") String name){
//		String result = demoService.get(name);
		String result = "My name is " + name;
		try {
			resp.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@EarsRequestMapping("/add")
	public void add(HttpServletRequest req, HttpServletResponse resp,
					@EarsRequestParam("a") Integer a, @EarsRequestParam("b") Integer b){
		try {
			resp.getWriter().write(a + "+" + b + "=" + (a + b));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@EarsRequestMapping("/sub")
	public void add(HttpServletRequest req, HttpServletResponse resp,
					@EarsRequestParam("a") Double a, @EarsRequestParam("b") Double b){
		try {
			resp.getWriter().write(a + "-" + b + "=" + (a - b));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@EarsRequestMapping("/remove")
	public String  remove(@EarsRequestParam("id") Integer id){
		return "" + id;
	}

}
