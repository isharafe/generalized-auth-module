docker build \
  -f Dockerfile \
  -t auth-module-dev-env .
 
 
 docker run -d \
  --name auth-module-dev-env \
  --hostname auth-module-dev-env \
  -v "/Users/ishara/Projects/auth-module":/workspace \
  -v codex-home:/home/developer/.codex \
  auth-module-dev-env
  
  
  -- from intellij terminal
  
  docker exec -it auth-module-dev-env bash
  