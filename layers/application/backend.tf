terraform {
  backend "s3" {
    # Backend configuration provided via backend-config.tfvars
    # The key is set per environment in backend-config.tfvars
  }
}
