FROM httpd:2.4

# Proxy 모듈 및 vhosts 활성화
RUN sed -i 's|#LoadModule proxy_module modules/mod_proxy.so|LoadModule proxy_module modules/mod_proxy.so|' /usr/local/apache2/conf/httpd.conf && \
    sed -i 's|#LoadModule proxy_http_module modules/mod_proxy_http.so|LoadModule proxy_http_module modules/mod_proxy_http.so|' /usr/local/apache2/conf/httpd.conf && \
    sed -i 's|#Include conf/extra/httpd-vhosts.conf|Include conf/extra/httpd-vhosts.conf|' /usr/local/apache2/conf/httpd.conf
