package com.matthewmitchell.peercoinj.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import android.text.TextUtils;

/**
 * format工具类
 * 
 * @author zm
 * 
 */
public class FormatUtils {

	public static String formatStr(long value){
		long money = Math.abs(value);
		Double moneyDouble = Double.valueOf(money);
		Double expDouble = Double.valueOf(100000000);
		DecimalFormat df = new DecimalFormat("#0.00000000");
		return df.format(moneyDouble/expDouble);
	}
}
