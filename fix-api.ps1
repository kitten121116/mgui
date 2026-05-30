# 修复 Minecraft 1.21.8 API 变更
$baseDir = "C:\ideapoj\template-template-1.21.8\src\client\java\com\danjvan\mgui"

Write-Host "开始修复 API 兼容性..." -ForegroundColor Green

# 获取所有 Java 文件
$files = Get-ChildItem -Path $baseDir -Filter "*.java" -Recurse

foreach ($file in $files) {
    Write-Host "处理: $($file.Name)" -ForegroundColor Cyan
    
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    
    # 替换 MinecraftClient -> Minecraft
    $content = $content -replace 'import net\.minecraft\.client\.MinecraftClient;', 'import net.minecraft.client.Minecraft;'
    $content = $content -replace 'MinecraftClient client = MinecraftClient\.getInstance\(\);', 'Minecraft client = Minecraft.getInstance();'
    $content = $content -replace 'MinecraftClient\.getInstance\(\)', 'Minecraft.getInstance()'
    
    # 替换 Text -> Component (如果还有残留)
    $content = $content -replace 'import net\.minecraft\.text\.Text;', 'import net.minecraft.network.chat.Component;'
    
    # 写回文件
    Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
}

Write-Host "`n修复完成！" -ForegroundColor Green
