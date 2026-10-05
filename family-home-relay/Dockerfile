FROM python:3.12-slim
WORKDIR /app
COPY server.py .
ENV PYTHONUNBUFFERED=1 FAMILY_DATA=/data
CMD ["python", "server.py"]
