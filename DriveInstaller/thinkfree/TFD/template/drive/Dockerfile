FROM rockylinux:8.9
ARG VERSION="1.3.0_20260430"
LABEL thinkfree_drive.version="${VERSION}"

RUN dnf -y install nodejs java-21-openjdk-devel procps-ng \
 && dnf clean all && rm -rf /var/cache/dnf

WORKDIR /root

COPY ./data/bin/            /root/bin/
COPY ./data/service/   /root/service/
COPY ./docker-entrypoint.sh /docker-entrypoint.sh

RUN chmod +x /docker-entrypoint.sh /root/bin/*.sh

EXPOSE 8080

ENTRYPOINT ["/docker-entrypoint.sh"]
