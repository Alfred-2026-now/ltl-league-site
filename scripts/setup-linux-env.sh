#!/bin/bash

# LTL League Backend - Linux环境安装脚本
# 支持Ubuntu/Debian/CentOS/Rocky Linux/AlmaLinux

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检测Linux发行版
detect_os() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
        OS_VERSION=$VERSION_ID
    else
        log_error "无法检测操作系统版本"
        exit 1
    fi

    log_info "检测到操作系统: $OS $OS_VERSION"
}

# 检查是否为root用户
check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "请使用root用户或sudo运行此脚本"
        exit 1
    fi
}

# 安装Java 17
install_java() {
    log_info "开始安装Java 17..."

    case $OS in
        ubuntu|debian)
            apt update
            apt install -y openjdk-17-jdk
            ;;
        centos|rhel|rocky|almalinux)
            # 安装OpenJDK 17
            if command -v dnf &> /dev/null; then
                dnf install -y java-17-openjdk java-17-openjdk-devel
            else
                yum install -y java-17-openjdk java-17-openjdk-devel
            fi
            ;;
        *)
            log_error "不支持的操作系统: $OS"
            exit 1
            ;;
    esac

    # 设置JAVA_HOME
    cat >> /etc/profile.d/java17.sh << 'EOF'
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
export PATH=$JAVA_HOME/bin:$PATH
EOF

    chmod +x /etc/profile.d/java17.sh
    source /etc/profile.d/java17.sh

    # 验证安装
    java -version
    log_info "Java 17 安装完成"
}

# 安装Maven
install_maven() {
    log_info "开始安装Maven..."

    MAVEN_VERSION="3.9.16"
    MAVEN_DOWNLOAD_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"

    # 下载并安装Maven
    cd /tmp
    wget -O maven.tar.gz $MAVEN_DOWNLOAD_URL
    tar -xzf maven.tar.gz -C /opt/
    mv /opt/apache-maven-${MAVEN_VERSION} /opt/maven

    # 设置MAVEN_HOME
    cat >> /etc/profile.d/maven.sh << 'EOF'
export MAVEN_HOME=/opt/maven
export PATH=$MAVEN_HOME/bin:$PATH
EOF

    chmod +x /etc/profile.d/maven.sh
    source /etc/profile.d/maven.sh

    # 验证安装
    mvn -version
    log_info "Maven 安装完成"

    # 清理临时文件
    rm -f /tmp/maven.tar.gz
}

# 配置防火墙（可选）
configure_firewall() {
    log_info "配置防火墙规则..."

    case $OS in
        ubuntu|debian)
            if command -v ufw &> /dev/null; then
                ufw allow 8080/tcp
                ufw reload
                log_info "UFW防火墙规则已配置"
            else
                log_warn "未检测到UFW，跳过防火墙配置"
            fi
            ;;
        centos|rhel|rocky|almalinux)
            if command -v firewall-cmd &> /dev/null; then
                firewall-cmd --permanent --add-port=8080/tcp
                firewall-cmd --reload
                log_info "Firewalld防火墙规则已配置"
            else
                log_warn "未检测到Firewalld，跳过防火墙配置"
            fi
            ;;
    esac
}

# 创建应用用户
create_app_user() {
    log_info "创建应用用户..."

    if ! id -u ltl &> /dev/null; then
        useradd -r -s /bin/bash -d /opt/ltl-league ltl
        log_info "用户ltl创建成功"
    else
        log_warn "用户ltl已存在"
    fi
}

# 创建应用目录
create_app_directories() {
    log_info "创建应用目录..."

    mkdir -p /opt/ltl-league/backend
    mkdir -p /opt/ltl-league/logs
    mkdir -p /var/www/ltl-league/uploads

    chown -R ltl:ltl /opt/ltl-league
    chown -R www-data:www-data /var/www/ltl-league
    chown -R ltl:ltl /var/www/ltl-league/uploads
    chmod 755 /var/www/ltl-league/uploads

    log_info "应用目录创建完成"
}

# 创建systemd服务文件
create_systemd_service() {
    log_info "创建systemd服务配置..."

    cat > /etc/systemd/system/ltl-league-backend.service << 'EOF'
[Unit]
Description=LTL League Backend Service
After=network.target mysql.service

[Service]
Type=simple
User=ltl
Group=ltl
WorkingDirectory=/opt/ltl-league/backend
Environment="JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))"
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="DB_USERNAME=ltl_user"
Environment="DB_PASSWORD=a5201314"
ExecStart=/opt/ltl-league/backend/start.sh
ExecStop=/opt/ltl-league/backend/stop.sh
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/ltl-league/logs/application.log
StandardError=append:/opt/ltl-league/logs/error.log

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    log_info "systemd服务配置已创建"
}

# 创建启动脚本
create_start_script() {
    log_info "创建启动脚本..."

    cat > /opt/ltl-league/backend/start.sh << 'EOF'
#!/bin/bash
cd /opt/ltl-league/backend
nohup java -jar target/league-backend-1.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=8080 \
  > /opt/ltl-league/logs/application.log 2>&1 &
echo $! > /opt/ltl-league/backend/app.pid
EOF

    chmod +x /opt/ltl-league/backend/start.sh
    chown ltl:ltl /opt/ltl-league/backend/start.sh

    # 创建停止脚本
    cat > /opt/ltl-league/backend/stop.sh << 'EOF'
#!/bin/bash
if [ -f /opt/ltl-league/backend/app.pid ]; then
    pid=$(cat /opt/ltl-league/backend/app.pid)
    kill $pid
    rm /opt/ltl-league/backend/app.pid
else
    # 尝试通过进程名查找并停止
    pkill -f "league-backend-1.0.0.jar"
fi
EOF

    chmod +x /opt/ltl-league/backend/stop.sh
    chown ltl:ltl /opt/ltl-league/backend/stop.sh

    log_info "启动脚本已创建"
}

# 主函数
main() {
    log_info "开始安装LTL League后端环境..."

    detect_os
    check_root
    install_java
    install_maven
    configure_firewall
    create_app_user
    create_app_directories
    create_systemd_service
    create_start_script

    log_info "环境安装完成！"
    log_info "接下来请："
    log_info "1. 将后端代码上传到 /opt/ltl-league/backend"
    log_info "2. 使用ltl用户运行: cd /opt/ltl-league/backend && mvn clean package"
    log_info "3. 启动服务: systemctl start ltl-league-backend"
    log_info "4. 设置开机自启: systemctl enable ltl-league-backend"
    log_info "5. 检查服务状态: systemctl status ltl-league-backend"
}

# 运行主函数
main
