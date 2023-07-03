import yt_dlp


def get_video_info(url):
    ydl_opts = {
        'format': 'best'
    }

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(url, download=False)
    title = info_dict.get('title', '')
    likes = info_dict.get('like_count', '')
    views = info_dict.get('view_count', '')
    thumbnail = info_dict.get('thumbnail', '')
    stream_url = info_dict['url']
    print(stream_url)

    return title, likes, views, thumbnail, stream_url
