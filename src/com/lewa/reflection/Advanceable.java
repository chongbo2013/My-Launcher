package com.lewa.reflection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Advanceable {
	public static void advance(Object advanceable){
		try {
			Class<?> cls = Class.forName("android.widget.Advanceable");
			if(cls == null){
				return;
			}
			
			Method m = cls.getDeclaredMethod("advance");
			if(m == null){
				return;
			}
			m.invoke(advanceable);			
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void fyiWillBeAdvancedByHostKThx(Object advanceable){
		try {
			Class<?> cls = Class.forName("android.widget.Advanceable");
			if(cls == null){
				return;
			}
			
			Method m = cls.getDeclaredMethod("fyiWillBeAdvancedByHostKThx");
			if(m == null){
				return;
			}
			m.invoke(advanceable);			
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
