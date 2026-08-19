package com.typenull.pingdom.analysis.application.pdf;

public interface HtmlToPdfConverter {

    byte[] convert(String html);
}
