package com.snapdeal.common.agents

class NodeAgent{
    def context
    def environment
    def stage
    def label

    NodeAgent(context, environment, stage) {
        this.context = context
        this.environment = environment
        this.stage = stage
        this.label = "env:" + this.environment
    }

    def withSlave(body) {
        try {
            this.context.node(this.label) {
                this.context.stage(this.stage) {
                    docker.withRegistry('http://dockerregistry.snapdeal.io:5000') {
                        docker.image('jenkins/slave:java')
                              .inside('-u root -v /root/.m2:/root/.m2 -v /etc/salt:/etc/salt -v /var/cache/salt/minion:/var/cache/salt/minion') {
                            body
                        }
                    }
                }
            }
        } catch (err) {
            currentBuild.result = 'FAILED'
            throw err
        }
    }
}
