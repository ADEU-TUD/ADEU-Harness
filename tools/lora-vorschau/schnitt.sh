#!/bin/bash
# Schneidet die Vergleichsfilme der Lora-Vorschau: links der alte Stand, rechts der neue (H.264, läuft überall).
#   tools/lora-vorschau/schnitt.sh <alt> <neu> <ziel>
# <alt> und <neu> sind Ordner von LoraPreviewTest (je light/ und dark/ mit .mkv), <ziel> bekommt light/ und dark/ mit .mp4.
set -e
ALT=$1; NEU=$2; OUT=$3
for look in light dark; do
  mkdir -p "$OUT/$look"
  for f in "$NEU/$look"/*.mkv; do
    n=$(basename "$f" .mkv)
    lower=$(echo "$n" | tr 'A-Z' 'a-z')
    crf=22
    case $n in reel-*) crf=25;; esac
    ffmpeg -loglevel error -y -i "$ALT/$look/$n.mkv" -i "$f" \
      -filter_complex "[0:v][1:v]hstack=inputs=2,scale=out_color_matrix=bt709:out_range=tv,format=yuv420p[v]" -map "[v]" \
      -c:v libx264 -preset slow -crf $crf -tune animation -g 120 -colorspace bt709 -color_primaries bt709 -color_trc bt709 \
      -movflags +faststart -an "$OUT/$look/$lower.mp4" &
    while [ "$(jobs -r | wc -l)" -ge 4 ]; do wait -n; done
  done
  wait
done
du -sh "$OUT/light" "$OUT/dark"
