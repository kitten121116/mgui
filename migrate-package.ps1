# MGUI 包名迁移脚本
# 将所有 com.example.wgewge.mgui 改为 com.danjvan.mgui

$oldBase = "C:\ideapoj\template-template-1.21.8\src\client\java\com\example\wgewge\mgui"
$newBase = "C:\ideapoj\template-template-1.21.8\src\client\java\com\danjvan\mgui"

Write-Host "开始迁移 MGUI 包名..." -ForegroundColor Green

# 复制整个目录结构
if (Test-Path $newBase) {
    Remove-Item $newBase -Recurse -Force
}

Copy-Item $oldBase $newBase -Recurse -Force

# 获取所有 Java 文件
$files = Get-ChildItem -Path $newBase -Filter "*.java" -Recurse

foreach ($file in $files) {
    Write-Host "处理文件: $($file.Name)" -ForegroundColor Cyan
    
    # 读取文件内容
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    
    # 替换包名
    $content = $content -replace 'package com\.example\.wgewge\.mgui', 'package com.danjvan.mgui'
    $content = $content -replace 'import com\.example\.wgewge\.mgui', 'import com.danjvan.mgui'
    
    # 写回文件
    Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
}

Write-Host "`n迁移完成！" -ForegroundColor Green
Write-Host "请删除旧目录: $oldBase" -ForegroundColor Yellow
