package step.plugins.java.handler;

import java.net.URLClassLoader;
import java.util.Arrays;

import step.handlers.javahandler.AbstractKeyword;
import step.handlers.javahandler.Keyword;

public class MyKeywordLibrary extends AbstractKeyword {

	@Keyword
	public void MyKeyword1() {
		output.add("MyKey", "MyValue");
	}
	
	@Keyword
	public void TestClassloader() {
		// the context classloader should be equal to the class loader of the keyword as many framework rely 
		// on context class loader lookup
		ClassLoader contextClassloader = Thread.currentThread().getContextClassLoader();
		try {
			assert contextClassloader instanceof URLClassLoader;
			output.add("clURLs", Arrays.toString(((URLClassLoader)contextClassloader).getURLs()));
		} catch(Exception e) {
			throw new AssertionError("Context CL was not an URLClassloader as expected but was: "+contextClassloader, e);
		}
	}
}
