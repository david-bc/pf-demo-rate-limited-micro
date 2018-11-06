# Running Locally

```bash
docker run --name rl -d -p 28080:8080 davidbc/pf-demo-rate-limited-micro:1.0.1
```


# Development

Be sure to update the version in the following files:

* `build.gradle`
* `Dockerfile`
* `dBuild.sh`
* `dInit.sh`
* `dPush.sh`
* `src/main/resources/application.yml`

## Build and Deploy
```bash
./gradlew build
./dBuild.sh

# docker images => get IMAGE ID
docker tag <IMAGE_ID> gcr.io/platform-architecture-poc/pf-demo-rate-limited-micro:<VERSION>

./dPush.sh

# https://cloud.google.com/kubernetes-engine/docs/tutorials/hello-app

kubectl set image deployment/pf-demo-rate-limited-micro pf-demo-rate-limited-micro=gcr.io/platform-architecture-poc/pf-demo-rate-limited-micro:1.0.1
```