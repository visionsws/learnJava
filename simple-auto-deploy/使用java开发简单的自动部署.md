### 背景：
作为一名开发人员，不可避免地需要部署项目。
在没使用Jenkins之前，每次部署都使人感觉到很繁琐。
那有没有办法自己弄一个程序来减轻一下部署的工作呢？
就是运行一下程序，想要部署的项目就直接部署到服务器中。

### 准备阶段
##### 一、了解部署项目的步骤过程
1. 将打包好的jar文件上传到服务器上
2. 查看项目是否在运行，运行的话就停止
3. 启动项目程序
##### 二、前置条件：
1. 一个简单的springboot项目
2. 一台配置好环境的服务器

### 实际开发：
##### 一、简单了解Jsch
JSch是Java Secure Channel的缩写。JSch是一个SSH2的纯Java实现。它允许你连接到一个SSH服务器，并且可以使用端口转发，X11转发，文件传输等，当然你也可以集成它的功能到你自己的应用程序。
这里只需使用JSch实现的SFTP功能
SFTP是Secure File Transfer Protocol的缩写，安全文件传送协议。可以为传输文件提供一种安全的加密方法。SFTP 为 SSH的一部份，是一种传输文件到服务器的安全方式。SFTP是使用加密传输认证信息和传输的数据，所以，使用SFTP是非常安全的。但是，由于这种传输方式使用了加密/解密技术，所以传输效率比普通的FTP要低得多。
ChannelSftp类是JSch实现SFTP核心类，它包含了所有SFTP的方法
##### 二、拆分步骤
要使用JSch将项目部署到服务器中，可拆分为如下步骤：
1、连接到服务器
2、创建新项目文件夹
3、将项目上传到新创建的文件夹中
4、查看进程并关闭正在运行的项目程序
5、启动项目程序

##### 三、具体程序代码
1. 添加依赖的jar包
```
<dependency>
   <groupId>com.jcraft</groupId>
   <artifactId>jsch</artifactId>
   <version>0.1.54</version>
  </dependency>
```
2. 连接到服务器
```java
    public void connect() throws JSchException {
        jsch = new JSch();
        session = jsch.getSession(user, host, port);
        session.setPassword(passwd);
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect();
        Channel channel = session.openChannel("sftp");
        channel.connect();
        sftp = (ChannelSftp) channel;
        log.info("连接到SFTP成功。host: " + host);
    }
```
3. 创建新项目文件夹
```
public void createDir(String createpath) {
        try {
            if (isDirExist(createpath)) {
                sftp.cd(createpath);
                return;
            }
            String pathArry[] = createpath.split("/");
            StringBuffer filePath = new StringBuffer("/");
            for (String path : pathArry) {
                if (path.equals("")) {
                    continue;
                }
                filePath.append(path + "/");
                if (isDirExist(filePath.toString())) {
                    sftp.cd(filePath.toString());
                } else {
                    // 建立目录
                    sftp.mkdir(filePath.toString());
                    // 进入并设置为当前目录
                    sftp.cd(filePath.toString());
                }
            }
            sftp.cd(createpath);
        } catch (SftpException e) {
            throw new RuntimeException("创建路径错误：" + createpath);
        }
    }
```
4. 将项目上传到新创建的文件夹中
```
public void uploadFile(String local,String remote) throws Exception {
        File file = new File(local);
        if (file.isDirectory()) {
            throw new RuntimeException(local + " is not a file");
        }

        InputStream inputStream = null;
        try {
            String rpath = remote.substring(0,remote.lastIndexOf("/")+1);
            if (!isDirExist(rpath)){
                log.info("目录不存在，创建目录:{}",rpath);
                createDir(rpath);
            }
            inputStream = new FileInputStream(file);
            sftp.setInputStream(inputStream);
            sftp.put(inputStream, remote);
            log.info("上传文件完成");
        } catch (Exception e) {
            log.info("上传文件失败");
            throw e;
        }finally{
            if(inputStream != null){
                inputStream.close();
            }
        }
    }
```
5. 远程执行Shell命令，执行完后返回结果
```
public int execCmd(String command) throws Exception{
        log.info( "开始执行命令:" + command);
        int returnCode = -1;
        BufferedReader reader = null;
        Channel channel = null;

        channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);
        channel.setInputStream(null);
        ((ChannelExec) channel).setErrStream(System.err);
        InputStream in = channel.getInputStream();
        reader = new BufferedReader(new InputStreamReader(in));

        channel.connect();
        log.info("The remote command is:{}" ,command);
        String buf ;
        while ((buf = reader.readLine()) != null) {
            log.info(buf);
        }
        reader.close();
        // Get the return code only after the channel is closed.
        if (channel.isClosed()) {
            returnCode = channel.getExitStatus();
        }
        log.info( "Exit-status:" + returnCode );

       /* StringBuffer buf = new StringBuffer( 1024 );
        byte[] tmp = new byte[ 1024 ];
        while ( true ) {
            while ( in.available() > 0 ) {
                int i = in.read( tmp, 0, 1024 );
                if ( i < 0 ) break;
                buf.append( new String( tmp, 0, i ) );
            }
            if ( channel.isClosed() ) {
                res = channel.getExitStatus();
                log.info( "Exit-status:" + res );
                System.out.println( "Exit-status:" + res );
                break;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        log.info( buf.toString() );*/

        channel.disconnect();
        return returnCode;
    }
```
6. 远程执行Shell命令，每当有一行结果就返回
```
public int shellCmd(String command) throws Exception{
        long startTime=System.currentTimeMillis();
        log.info( "开始执行命令:" + command);
        int returnCode = -1;
        boolean isTimeOut = false;
        ChannelShell channel=(ChannelShell) session.openChannel("shell");
        InputStream in = channel.getInputStream();
        channel.setPty(true);
        channel.connect();
        OutputStream os = channel.getOutputStream();
        os.write((command + "\r\n").getBytes());
        os.write("exit\r\n".getBytes());
        os.flush();
        log.info("The remote command is:{}" ,command);
        byte[] tmp=new byte[1024];
        while(true) {
            while (in.available() > 0) {
                int i = in.read(tmp, 0, 1024);
                if (i < 0) break;
                log.info(new String(tmp, 0, i));
            }
            if (channel.isClosed()) {
                if (in.available() > 0) continue;
                returnCode = channel.getExitStatus();
                log.info("exit-status: " + channel.getExitStatus());
                break;
            }
            long useTime=System.currentTimeMillis()-startTime;
            if (useTime> 600000){
                log.info("[error] Execution command timeout!" );
                isTimeOut = true;
                break;
            }
            try{Thread.sleep(1000);}catch(Exception ee){}
        }
        long endTime=System.currentTimeMillis();
        float excTime=(float)(endTime-startTime)/1000;
        log.info("执行命令是否超时:{},共耗时:{}s",isTimeOut,excTime);
        os.close();
        in.close();
        channel.disconnect();
        session.disconnect();
        return returnCode;
    }
```
7. 具体的执行，如需要部署simple-auto-deploy.jar
```
 public static void main(String[] args) {
        JSchExecutor jSchUtil = new JSchExecutor( "123", "123123","192.168.243.18");
        try {
            jSchUtil.connect();
            jSchUtil.execCmd("kill -9 `ps -ef | grep simple-auto-deploy.jar | grep -v grep | awk '{print $2}'`");
            jSchUtil.uploadFile("C:\\mywork\\Workspaces\\IdeaProjects\\simple-auto-deploy\\target\\simple-auto-deploy.jar","/data/simple-auto-deploy/simple-auto-deploy.jar");
            jSchUtil.shellCmd("nohup java -jar /data/simple-auto-deploy/simple-auto-deploy.jar >/dev/null &");
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            jSchUtil.disconnect();
        }

    }
```

### 总结
这样一个简单的自动部署就出来了，运行main方法，程序就自动部署到服务器中。
代码可参考：https://github.com/visionsws/learnJava 中的 [simple-auto-deploy](https://github.com/visionsws/learnJava)






