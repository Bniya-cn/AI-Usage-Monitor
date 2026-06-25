<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI Usage Monitor - Web Dashboard</title>
    <!-- 引入由 Vite 打包编译生成的 CSS 样式 -->
    <link rel="stylesheet" href="assets/index.css">
    <!-- Google Fonts Outfit & Inter -->
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Outfit:wght@400;600;700&display=swap" rel="stylesheet">
    <style>
        /* 简单加载动画，在 Vue 3 挂载前展示 */
        .server-welcome {
            position: fixed;
            top: 0;
            left: 0;
            width: 100vw;
            height: 100vh;
            background-color: #0c0b14;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            z-index: 9999;
            color: #ffffff;
            font-family: 'Inter', sans-serif;
            transition: opacity 0.5s ease-out;
        }
        .loader {
            border: 3px solid rgba(255, 255, 255, 0.05);
            border-top: 3px solid #6366f1;
            border-radius: 50%;
            width: 40px;
            height: 40px;
            animation: spin 1s linear infinite;
            margin: 0 auto 20px;
        }
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
        .welcome-title {
            font-family: 'Outfit', sans-serif;
            font-size: 20px;
            font-weight: 600;
            letter-spacing: 1px;
            background: linear-gradient(135deg, #a5b4fc, #6366f1);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
    </style>
</head>
<body>
    <div id="app">
        <!-- Vue 未挂载前的 Loading 状态 -->
        <div class="server-welcome">
            <div class="loader"></div>
            <div class="welcome-title">AI Usage Monitor Loading...</div>
        </div>
    </div>
    
    <!-- 引入由 Vite 打包编译生成的 Vue SPA 应用入口 -->
    <script type="module" src="assets/index.js"></script>
</body>
</html>
