# Use an official lightweight Python image
FROM python:3.10-slim

# Set working directory inside the container
WORKDIR /app

# Install dependencies directly to speed up startup
RUN pip install --no-cache-dir fastapi uvicorn pydantic

# Copy python app assets
COPY ./main.py /app/main.py
COPY ./admin /app/admin

# Expose backend API default port
EXPOSE 8000

# Command to execute production server
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
