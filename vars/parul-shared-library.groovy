#!/usr/bin/groovy
import groovy.json.JsonSlurper

@NonCPS
def parseJson(text) {
  return new JsonSlurper().parseText(text)
}

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def log_level = "quiet"
  if(DEBUG) {
    log_level = "debug"
  }

  def docker_image = "dockerregistry.snapdeal.io:5000/jenkins/slave:java"
  def approvers = []

  stage('setup') {
    node("env:dev", {
      docker.image(docker_image)
        .inside('-u root -v /etc/salt:/etc/salt') {
          // create a function to do this. @todo princetyagi
          sh("salt-call saltutil.sync_all -l ${log_level}")
          def data = parseJson(sh(returnStdout: true, script: "salt-call gitlab.subcomponent ${config.name} -l ${log_level} --out json")).local
          repo = data.deploy.repository.toString()
          repo = repo.replaceAll("http://", "").replaceAll("git@", "").replaceAll('gitlab.snapdeal.com/', 'gitlab.snapdeal.com:').replaceAll('.git', '');
          env.gitlab_url = "git@${repo}.git"
      }
    })
  }

  stage('clone') {
    node("env:dev", {
      docker.image(docker_image)
        .inside('-u root') {
          checkout([
            $class: 'GitSCM',
            branches: [[name: "origin/${BRANCH}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[$class: 'CleanCheckout']],
            submoduleCfg: [],
            userRemoteConfigs: [[url: env.gitlab_url]]
          ])
          if(fileExists('./infra/setup')) {
            env.infra_setup = "1"
          }
          if(fileExists('./infra/prebuildtests')) {
            env.infra_prebuildtests = "1"
          }
          if(fileExists('./infra/postbuildtests')) {
            env.infra_postbuildtests = "1"
          }
      }
    })
  }

  node("env:dev", {
    try {
      stage ('pre-build') {
        parallel 'tests': {
          docker.image(docker_image)
            .inside('-u root') {
              if(env.infra_prebuildtests) {
                sh "chmod +x ./infra/prebuildtests"
                sh "./infra/prebuildtests"
              }
          }
        }
      }
//        },
//        'infra/setup': {
//          docker.image(docker_image)
//            .inside('-u root') {
//              if(env.infra_setup) {
//                sh "chmod +x ./infra/setup"
//                sh "./infra/setup"
//              }
//          }
//        }
    } catch (err) {
      currentBuild.result = 'FAILED'
      throw err
    }
  })

  stage('build') {
    node("env:dev", {
      docker.image(docker_image)
        .inside('-u root -v /etc/salt:/etc/salt') {
        // adding infra setup
          sh "git clean -fdx"
          if(env.infra_setup) {
            sh "chmod +x ./infra/setup"
            sh "./infra/setup" 
            }
          sh("salt-call saltutil.sync_all -l ${log_level}")
          sh("salt-call -l ${log_level} shipit.build ${config.name}")
          sh("salt-call -l ${log_level} shipit.package ${config.name}")
          stash includes: "*.rpm", name: 'rpm', excludes: ".git*"
          stash includes: "release.version", name: 'release.version', excludes: ".git*"
          archiveArtifacts artifacts: "*.rpm", excludes: null, fingerprint: true, onlyIfSuccessful: true
      }
    })
  }

  node("env:dev", {
    try {
      stage ('post-build') {
        parallel 'tests': {
          docker.image(docker_image)
            .inside('-u root') {
              if(env.infra_postbuildtests) {
                sh "chmod +x ./infra/postbuildtests"
                sh "./infra/postbuildtests"
              }
          }
        },
        'dockerize': {
          docker.image(docker_image)
            .inside('-u root -v /etc/salt:/etc/salt -v /var/run/docker.sock:/var/run/docker.sock') {
              dir("./docker") {
                unstash 'rpm'
                unstash 'release.version'
                sh "salt-call -l ${log_level} saltutil.sync_all"
                sh "salt-call -l ${log_level} shipit.dockerize ${config.name}"
              }
          }
        },
        'databags': {
          docker.image(docker_image)
            .inside('-u root') {
              sh "echo check."
          }
        }
      }
    } catch (err) {
      currentBuild.result = 'FAILED'
      throw err
    }
  })

  if ('qa' in ENV.tokenize(',')) {
    stage('wait (7 DAYS)') {
      timeout(time: 7, unit: 'DAYS') {
        // add maintainers section in services yaml. also add devops users.
        input message: "Proceed to dev?", ok: 'Yes'//, submitter: data
      }
    }

    stage('promote (dev)') {
      node("env:dev", {
        docker.image(docker_image)
          .inside('-u root -v /etc/salt:/etc/salt') {
            sh("salt-call saltutil.sync_all -l ${log_level}")
            sh("salt-call -l ${log_level} state.sls promote 'pillar={\"app_name\":\"${config.name}\",\"build_number\":\"${env.build_number}\"}'")
        }
      })
    }

    stage('deploy (dev)') {
      node("env:dev", {
        docker.image(docker_image)
          .inside('-u root -v /etc/salt:/etc/salt') {
            sh("salt-call saltutil.sync_all -l ${log_level}")
            sh("salt-call -l ${log_level} state.sls deploy 'pillar={\"app_name\":\"${config.name}\"}'")
        }
      })
    }
  }

  if('stg' in ENV.tokenize(',') && (BRANCH.startsWith('release-') || BRANCH == 'master')) {
    stage('wait (3 DAYS)') {
      timeout(time: 3, unit: 'DAYS') {
        // add maintainers section in services yaml. also add devops users.
        input message: "Proceed to stg?", ok: 'Yes'//, submitter: data
      }
    }

    stage('promote (stg)') {
      node("env:stg", {
        docker.image(docker_image)
          .inside('-u root -v /etc/salt:/etc/salt') {
            sh("salt-call saltutil.sync_all -l ${log_level}")
            sh("salt-call -l ${log_level} state.sls promote 'pillar={\"app_name\":\"${config.name}\",\"build_number\":\"${env.build_number}\"}'")
        }
      })
    }

    stage('deploy (stg)') {
      node("env:stg", {
        docker.image(docker_image)
          .inside('-u root -v /etc/salt:/etc/salt') {
            sh("salt-call saltutil.sync_all -l ${log_level}")
            sh("salt-call -l ${log_level} state.sls deploy 'pillar={\"app_name\":\"${config.name}\"}'")
        }
      })
    }
  }

  if('prd' in ENV.tokenize(',') && (BRANCH.startsWith('release-') || BRANCH == 'master')) {
    stage('wait (3 HOURS)') {
      timeout(time: 3, unit: 'HOURS') {
        // add maintainers section in services yaml. also add devops users.
        input message: "Proceed to prd?", ok: 'Yes'//, submitter: data
      }
    }

    stage('promote (prd)') {
      node("env:prd", {
        docker.image(docker_image)
          .inside('-u root -v /etc/salt:/etc/salt') {
            sh("salt-call saltutil.sync_all -l ${log_level}")
            sh("salt-call -l ${log_level} state.sls promote 'pillar={\"app_name\":\"${config.name}\",\"build_number\":\"${env.build_number}\"}'")
        }
      })
    }

    stage('deploy (prd)') {
      node("env:prd", {
        docker.image(docker_image)
          .inside('-u root -v /etc/salt:/etc/salt') {
            sh("salt-call saltutil.sync_all -l ${log_level}")
            sh("salt-call -l ${log_level} state.sls deploy 'pillar={\"app_name\":\"${config.name}\"}'")
        }
      })
    }
  }

}


