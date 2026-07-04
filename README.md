# 🔴 RedstoneChip — 红石芯片

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-yellow)](https://fabricmc.net)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

**RedstoneChip** 是一个 Minecraft Fabric 模组，为红石工程设计提供了一系列增强工具。包括**强化红石粉**、**超导红石粉**（信号不衰减）、**强化中继器**和**强化比较器**。

---

## ✨ 特性

### 🧱 强化红石粉（16色）
- 全 16 种颜色可选
- 与普通红石粉行为一致，但**抗爆性更高**
- 适合在易受破坏的环境中使用

### ⚡ 超导红石粉（16色）
- 全 16 种颜色可选
- **信号强度不衰减** — 无论传输多远，输出始终为输入强度
- 2×2 棋盘格纹理设计，与强化红石粉在视觉上区分
- 整条连通网络作为一个整体计算信号，统一开关

### 🔁 强化中继器
- 完全对齐原版中继器逻辑
- 1–4 档延迟可调
- 可被侧向输入锁定

### ⚖️ 强化比较器
- 完全对齐原版比较器逻辑
- 支持比较模式与减法模式
- 方块实体实现，兼容性好

---

## 📦 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/)（Minecraft 1.21.1）
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api)
3. 将本模组的 `.jar` 文件和 Fabric API 放入 `.minecraft/mods/` 文件夹
4. 启动游戏

---

## 🛠️ 开发

```bash
# 克隆仓库
git clone https://github.com/ACupOfHim/better-redstone.git
cd better-redstone

# 编译
./gradlew build

# 运行客户端测试
./gradlew runClient
```

**环境要求：**
- Java 21
- Gradle（通过 `gradlew` 自动下载）

---

## 📁 项目结构

```
src/main/java/com/yourname/redstonechip/
├── RedstoneChipMod.java          # 主入口，注册方块
├── RedstoneChipModClient.java    # 客户端初始化
└── block/
    ├── ReinforcedRedstoneBlock.java         # 强化红石粉
    ├── SuperconductingRedstoneBlock.java    # 超导红石粉
    ├── ReinforcedRepeaterBlock.java         # 强化中继器
    ├── ReinforcedComparatorBlock.java       # 强化比较器
    └── entity/
        └── ReinforcedComparatorBlockEntity.java  # 比较器方块实体

src/main/resources/
├── assets/redstone_chip/         # 纹理、模型、语言文件
├── data/                         # 配方、战利品表
└── fabric.mod.json               # 模组元数据
```

---

## 🎨 方块图鉴

| 方块 | 信号衰减 | 颜色 | 外观特征 |
|------|---------|------|---------|
| 强化红石粉 | ✅ 有衰减 | 16色 | 纯色线，与普通红石粉外观相似 |
| **超导红石粉** | ❌ 无衰减 | 16色 | **2×2 棋盘格纹理**，一看就知道不会衰减 |
| 强化中继器 | — | — | 抗爆，1-4档延迟 |
| 强化比较器 | — | — | 抗爆，比较/减法模式 |

---

## 📝 License

MIT © ACupOfHim
