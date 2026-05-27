import requests
import json

# 接口配置
url = "https://japi.suyueyun.com/api/dev/lingxi/admin/login"

# 请求头
headers = {
    "Content-Type": "application/json",
    "productCode": "fengine"
}

# 请求体
payload = {
    "username": "jishuai",
    "password": "jsai#12345"
}

try:
    # 发送请求
    response = requests.post(url, headers=headers, json=payload, timeout=10)

    # 打印响应状态码
    print(f"HTTP状态码: {response.status_code}")
    print(f"响应内容: {response.text}")

    # 解析JSON
    result = response.json()

    # 判断登录是否成功
    if result.get("success") and result.get("code") == 200:
        print("\n✅ 登录成功！")
        print(f"Token: {result['data']['token']}")
        print(f"用户名: {result['data']['userVo']['userName']}")
        print(f"昵称: {result['data']['userVo']['nickName']}")
        print(f"用户ID: {result['data']['userVo']['userId']}")
    else:
        print(f"\n❌ 登录失败: {result.get('message')}")

except requests.exceptions.RequestException as e:
    print(f"❌ 请求异常: {e}")