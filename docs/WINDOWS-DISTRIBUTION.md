# Windows 分发（Setup.exe + 静默 MSI）

本文描述 `Distribution/Windows/`：**产品分发能力**，与主业务客户端 [`Windows/`](../Windows/) 物理隔离。

## 产物

分发一共 **三份**（均在 `artifacts/windows/`，已 gitignore）：

| 文件 | 界面 | 适合谁 |
|------|------|--------|
| `ZMusic-Setup.exe` | 自研沉浸式向导 | 喜欢动效与自定义体验；对多数用户推荐 |
| `ZMusic-Silent.msi` | 无 MSI UI | 喜欢「纯 MSI、可控、可脚本」；IT / `msiexec /qn` |
| `ZMusic-UI.msi` | Windows Installer 标准向导 | 想要 `.msi` 但需要官方风格安装界面 |

`ZMusic-Setup.exe` **内嵌 Silent MSI**，由自定义界面静默调用；不与 UI MSI 混用同一安装会话即可。Silent / UI 共用同一 ProductCode，为本版本的两种外壳，请按渠道二选一安装，勿在同一台机器先后混装不同外壳的同版本包。

与 Android 共用仓库根 `artifacts/`：

```text
artifacts/
  android/   # Distribution/Android/build.py
  windows/   # 上述三份
```

源码要约：[https://github.com/AboutUip/ZMusic](https://github.com/AboutUip/ZMusic)

安装后：

- 程序目录含 `ZMusic.exe`、`Uninstall.exe`、`LICENSE`、`NOTICE.txt`、`ThirdPartyNotices.txt`
- 「应用和功能」卸载项指向 `Uninstall.exe`（自定义 ARP；MSI 默认 ARP 已隐藏）

## 向导页

1. 首页  
2. 协议（GPL-2.0 全文 + 署名 / 第三方致谢；须勾选同意）  
3. 选择配置（单用户 / 整机；桌面与开始菜单快捷方式）  
4. 安装位置  
5. 进行安装  
6. 安装结束（可启动应用）

整机安装时，Setup 以 `msiexec` + UAC（`runas`）提升权限；单用户默认不提权。

## 构建

前置：Python 3、[.NET 9 SDK](https://dotnet.microsoft.com/download)（含 Windows Desktop）。WiX 通过 NuGet SDK `WixToolset.Sdk` 还原，**无需**全局安装 WiX CLI。

```bash
cd Distribution/Windows
python build.py
```

常用参数：

```bash
python build.py --configuration Release --runtime win-x64
python build.py --skip-sign          # 默认也会在无证书时跳过签名
python build.py --skip-setup         # 只出两份 MSI（调试用）
```

管线：`publish` 客户端 → Uninstall → **Silent MSI** → **UI MSI** → 嵌入 Silent 并 `publish` Setup → 可选签名。

静默安装示例：

```bash
# 整机（需管理员）
msiexec /i ZMusic-Silent.msi /qn INSTALLDIR="C:\Program Files\ZMusic" ALLUSERS=1

# 当前用户（勿写 ALLUSERS= 空值；须成对使用下列属性）
msiexec /i ZMusic-Silent.msi /qn INSTALLDIR="%LOCALAPPDATA%\Programs\ZMusic" MSIINSTALLPERUSER=1 ALLUSERS=2
```

## 卸载与数据清单

`Uninstall.exe` 流程：

1. UI 确认；默认勾选「同时删除本地登录数据与缓存」
2. `msiexec /x {ProductCode} /qn` 移除程序文件与快捷方式  
3. 若勾选清除数据：删除清单内用户数据路径  
4. 清理残留的 `%TEMP%\ZMusic-Setup-*`

契约见代码 [`ZMusic.Distribution.Shared/InstallInventory.cs`](../Distribution/Windows/ZMusic.Distribution.Shared/InstallInventory.cs)：

| 类别 | 路径 | 安装创建 | 卸载 |
|------|------|--------|------|
| 程序 | `INSTALLDIR\*` | 是 | MSI |
| 快捷方式 | 桌面 / 开始菜单 | 按选项 | MSI |
| ARP | `Uninstall\ZMusic` | 是 | MSI |
| 会话等 | `%LocalAppData%\ZMusic\**`（含 `session.dat`） | 否（运行时） | 默认删除 |
| Setup 临时目录 | `%TEMP%\ZMusic-Setup-*` | 安装期 | 安装/卸载结束清理 |

静默卸载（系统 QuietUninstallString）：

```text
Uninstall.exe /silent
Uninstall.exe /silent /keepdata
```

**防泄漏**：安装/卸载日志不得写入 Cookie / Token；默认清除会话；Setup/Uninstall 无遥测上报。

新增会存放敏感信息的运行时路径时，必须先登记 `InstallInventory`，再允许卸载器清理。

## 协议与署名

- 协议页嵌入仓库根 [`LICENSE`](../LICENSE)（GPL-2.0）
- [`assets/NOTICE.txt`](../Distribution/Windows/assets/NOTICE.txt)、[`assets/ThirdPartyNotices.txt`](../Distribution/Windows/assets/ThirdPartyNotices.txt) 随安装目录落地
- 发行前请确认 `ProductIdentity.SourceOfferUrl` 为 [https://github.com/AboutUip/ZMusic](https://github.com/AboutUip/ZMusic)（或你分发二进制时所对应的源码地址）

## Authenticode 签名（可选）

与 Android `keystore` **不是同一套**。减轻 SmartScreen「未知发布者」需对 **Setup.exe、ZMusic.exe、Uninstall.exe、MSI** 做代码签名。

### 如何获取证书

1. **商业 CA**：向 DigiCert、Sectigo、GlobalSign、SSL.com 等购买 **Code Signing** 证书（OV 或 EV；EV 通常更快建立 SmartScreen 信誉）。  
2. **云签名**：Microsoft **Azure Trusted Signing**（按签名次数，适合 CI）。  
3. **开源赞助**：可关注 [SignPath.io](https://signpath.io) 等面向 OSS 的签名计划（需申请）。

### 本地配置

1. 复制 [`signing.properties.example`](../Distribution/Windows/signing.properties.example) → `Distribution/Windows/signing.properties`  
2. 填入 `pfxFile`、`pfxPassword`、`timestampUrl`  
3. 安装 [Windows SDK](https://developer.microsoft.com/windows/downloads/windows-sdk/) 以便使用 `signtool`  
4. 运行 `python build.py`（存在有效 PFX 时自动签名）

`signing.properties`、`*.pfx` **不得提交仓库**（见根 `.gitignore`）。

手动签名示例：

```powershell
signtool sign /fd SHA256 /td SHA256 /tr http://timestamp.digicert.com /f your.pfx /p ***** ZMusic-Setup.exe
```

不做签名也可以本地/开源自用；对外分发时 Windows 可能持续警告。

## 目录

```
Distribution/Windows/
  ZMusic.Distribution.Shared/   # ProductCode、安装清单、msiexec 封装
  ZMusic.Setup/                 # 自定义向导（嵌入 Silent MSI）
  ZMusic.Uninstall/             # Uninstall.exe
  ZMusic.Msi/                   # Fragments + Silent / UI 两套 WiX 工程
  assets/                       # LICENSE / NOTICE / License.rtf / ThirdPartyNotices
  build.py
  signing.properties.example
```

## 标识符

与 `ProductIdentity` 对齐（升级时谨慎变更）：

- UpgradeCode：`{B7E4C2A1-8F3D-4E91-9C06-2A5B8D7E1F40}`（发布后勿改）  
- ProductCode：`{C8F5D3B2-9E4A-5F02-AD17-3B6C9E8F2051}`（本版本线；大版本升级可换）
