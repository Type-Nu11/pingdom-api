package com.typenull.pingdom.engagement.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostReportCreatedEventListener {

    @EventListener
    public void handle(PostReportCreatedEvent event) {
        Long postReportId = event.postReportId();


    }
}