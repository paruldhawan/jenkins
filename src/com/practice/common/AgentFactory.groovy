package com.practice.common

import com.practice.common.agents.*

class AgentFactory {
    def environment
    def context
    def stage

    AgentFactory(context, environment, stage) {
        this.context = context
        this.environment = environment
        this.stage = stage
    }

    def getAgent(){
        switch(this.environment) {
            default:
                return new NodeAgent(this.context, this.environment, this.stage)
        }
    }
}
