---
title: Kubernetes 部署
sidebar_position: 2
description: 在 Kubernetes 集群中部署 SkillHub
---

# Kubernetes 部署

本文介绍如何在 Kubernetes 集群中部署 SkillHub。

## 前置要求

- Kubernetes 1.24+
- kubectl 配置完成
- Helm 3.0+（可选）
- 可用的持久化存储类

## 部署清单

项目提供了 Kubernetes 部署清单：

```bash
cd deploy/k8s

# 1. 创建命名空间
kubectl apply -f 00-namespace.yml

# 2. 配置 Secret
cp 02-secret.example.yml 02-secret.yml
# 编辑 02-secret.yml 填入真实凭证

# 3. 应用配置
kubectl apply -f 01-configmap.yml
kubectl apply -f 02-secret.yml

# 4. 部署服务
kubectl apply -f 06-services.yaml
kubectl apply -f 03-01-scanner-deployment.yaml
kubectl apply -f 03-backend-deployment.yml
kubectl apply -f 04-frontend-deployment.yml

# 5. 配置 Ingress
kubectl apply -f 05-ingress.yml
```

## 高可用配置

- 后端和前端建议至少部署 2 个副本
- PostgreSQL 使用主从复制
- Redis 使用 Sentinel 或 Cluster 模式
- 存储使用高可用对象存储（如 MinIO 集群或云厂商 OSS）

## 下一步

- [配置说明](./configuration) - 详细配置项说明
