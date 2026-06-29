# -*- coding: utf-8 -*-
"""
将景点数据分段（Chunking）
按自然段落切分，保留元数据
"""
import json
import re

def split_into_chunks(content, min_chunk_size=200, max_chunk_size=800):
    """
    将文本内容按段落切分
    - 每个段落单独成chunk
    - 段落过长则进一步切分
    """
    if not content:
        return []

    # 按换行符分割段落
    paragraphs = content.split('\n')
    paragraphs = [p.strip() for p in paragraphs if p.strip()]

    chunks = []

    for para in paragraphs:
        para_len = len(para)

        # 段落太短，跳过
        if para_len < 50:
            continue

        # 段落长度合适，直接作为一个chunk
        if para_len <= max_chunk_size:
            chunks.append(para)
        else:
            # 段落过长，按句子再切分
            sentences = re.split(r'([。！？；\n])', para)
            current_chunk = ""

            for i in range(0, len(sentences) - 1, 2):
                sentence = sentences[i] + sentences[i + 1]
                if len(current_chunk) + len(sentence) <= max_chunk_size:
                    current_chunk += sentence
                else:
                    if current_chunk:
                        chunks.append(current_chunk.strip())
                    # 如果单个句子就超过max，则强制截断
                    if len(sentence) > max_chunk_size:
                        # 按字符数强制切分
                        for j in range(0, len(sentence), max_chunk_size):
                            chunks.append(sentence[j:j + max_chunk_size])
                        current_chunk = ""
                    else:
                        current_chunk = sentence

            if current_chunk.strip():
                chunks.append(current_chunk.strip())

    return chunks

def process_attractions(input_file, output_file):
    """处理景点数据，进行分段"""
    with open(input_file, 'r', encoding='utf-8') as f:
        attractions = json.load(f)

    all_chunks = []

    for attraction in attractions:
        city = attraction.get('city', '')
        title = attraction.get('title', '')
        content = attraction.get('content', '')
        source_url = attraction.get('source_url', '')

        # 提取info中的关键信息作为补充上下文
        info = attraction.get('info', {})
        info_text = ""
        if info:
            info_parts = []
            for k, v in info.items():
                if v and len(v) < 100:  # 简短信息才保留
                    info_parts.append(f"{k}: {v}")
            if info_parts:
                info_text = "【基本信息】" + "；".join(info_parts)

        # 分段
        chunks = split_into_chunks(content)

        for idx, chunk in enumerate(chunks):
            # 如果有info，追加到chunk末尾
            if info_text and idx == 0:
                chunk_with_info = chunk + "\n\n" + info_text
            else:
                chunk_with_info = chunk

            chunk_item = {
                "city": city,
                "title": title,
                "chunk_index": idx,
                "content": chunk_with_info,
                "source_url": source_url
            }
            all_chunks.append(chunk_item)

    # 保存结果
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(all_chunks, f, ensure_ascii=False, indent=2)

    # 统计
    stats = {}
    for c in all_chunks:
        city = c['city']
        stats[city] = stats.get(city, 0) + 1

    print(f"分段完成！")
    print(f"景点数: {len(attractions)}")
    print(f"总chunk数: {len(all_chunks)}")
    print(f"\n各城市chunk分布:")
    for city, count in sorted(stats.items()):
        print(f"  {city}: {count} chunks")

    print(f"\n已保存到: {output_file}")

    return all_chunks

if __name__ == "__main__":
    process_attractions(
        'guangdong_attractions_detail_fixed.json',
        'attractions_chunks.json'
    )