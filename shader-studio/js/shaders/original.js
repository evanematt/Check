export default {
  id: 'original',
  name: 'Original',
  category: 'Basic',
  tags: ['none', 'source', 'plain'],
  params: [],
  fragment: /* glsl */ `
void main() {
  outColor = texture(u_image, v_uv);
}`,
};
